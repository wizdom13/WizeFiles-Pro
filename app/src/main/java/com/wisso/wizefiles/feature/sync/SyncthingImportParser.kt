// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import java.io.StringReader
import java.util.Base64
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.json.JSONObject
import org.w3c.dom.Element
import org.xml.sax.InputSource

internal object SyncthingImportParser {
    fun parse(bytes: ByteArray, password: CharArray): SyncthingImportModel {
        require(bytes.size <= SyncthingBackupCodec.MAX_BYTES)
        if (SyncthingBackupCodec.encrypted(bytes)) {
            val plain = SyncthingBackupCodec.decrypt(bytes, password)
            return try { portable(JSONObject(plain.toString(Charsets.UTF_8))) } finally { plain.fill(0) }
        }
        if (bytes.size >= 2 && bytes[0] == 80.toByte() && bytes[1] == 75.toByte()) return archive(bytes)
        return xml(bytes.toString(Charsets.UTF_8), null, null, emptyMap())
    }

    private fun archive(bytes: ByteArray): SyncthingImportModel {
        val files = mutableMapOf<String, ByteArray>()
        var size = 0
        var count = 0
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++count <= 512) { "Too many files in configuration archive" }
                val path = entry.name
                require(!path.startsWith('/') && '\\' !in path && ':' !in path &&
                    path.split('/').none { it == ".." || it == "." }) { "Unsafe archive path" }
                if (entry.isDirectory) continue
                val content = SyncthingBackupCodec.read(zip)
                size += content.size
                require(size <= SyncthingBackupCodec.MAX_BYTES) { "Expanded archive exceeds 12 MiB" }
                require(files.put(path, content) == null) { "Duplicate archive entry" }
            }
        }
        fun unique(name: String): ByteArray? {
            val matches = files.filterKeys { it.substringAfterLast('/') == name }.values
            require(matches.size <= 1) { "Duplicate identity or configuration file" }
            return matches.singleOrNull()
        }
        val config = requireNotNull(unique("config.xml")) { "Archive has no config.xml" }
        val ignores = files.filterKeys { it.contains("ignores/") && it.endsWith(".stignore") }.mapKeys { (path, _) ->
            path.substringAfter("ignores/").removeSuffix("/.stignore").removeSuffix(".stignore")
        }.mapValues { (_, value) -> value.toString(Charsets.UTF_8).lines() }
        return xml(config.toString(Charsets.UTF_8), unique("cert.pem"), unique("key.pem"), ignores)
    }

    private fun xml(xml: String, cert: ByteArray?, key: ByteArray?, ignores: Map<String, List<String>>): SyncthingImportModel {
        require(xml.length <= 2 * 1024 * 1024 && !xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true)) {
            "Invalid Syncthing XML configuration"
        }
        val builder = DocumentBuilderFactory.newInstance().apply { isExpandEntityReferences = false }.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> throw java.io.IOException("External XML entities are disabled") }
        val root = builder.parse(InputSource(StringReader(xml))).documentElement
        require(root.tagName == "configuration")
        val identity = if (cert != null && key != null) SyncthingIdentity(cert, key) else null
        require(key == null || cert != null) { "The identity certificate is missing" }
        val ownId = identity?.validate() ?: cert?.let {
            val parsed = java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(it.inputStream())
            SyncthingIdentity.deviceId(parsed.encoded)
        }.orEmpty()
        val devices = root.children("device").map { device ->
            SyncthingDevice(device.getAttribute("id"), device.getAttribute("name"),
                device.children("address").map { it.textContent }.ifEmpty { listOf("dynamic") })
        }
        val folders = root.children("folder").map { folder ->
            require(folder.getAttribute("filesystemType") in setOf("", "basic")) { "Only local basic filesystems can be imported" }
            val mode = when (folder.getAttribute("type")) {
                "sendreceive", "" -> SyncMode.TWO_WAY
                "sendonly" -> SyncMode.MIRROR
                else -> throw IllegalArgumentException("Only Send & Receive and Send Only folders can be imported")
            }
            require(folder.children("device").none { it.getAttribute("encryptionPassword").isNotEmpty() }) {
                "Encrypted folder shares are not supported"
            }
            val versioning = folder.children("versioning").singleOrNull()
            require(versioning?.getAttribute("type") in setOf(null, "", "simple")) { "Only simple version retention can be imported" }
            require(versioning?.children("fsPath")?.all { it.textContent.isBlank() } != false) {
                "Custom version storage paths must be migrated separately"
            }
            val keep = if (versioning?.getAttribute("type") == "simple") versioning.children("param")
                .firstOrNull { it.getAttribute("key") == "keep" }?.getAttribute("val")?.toIntOrNull()?.coerceIn(1, 100) ?: 5 else 0
            val id = folder.getAttribute("id")
            SyncthingImportedFolder(id, folder.getAttribute("label").ifBlank { id }, folder.getAttribute("path"), mode,
                folder.children("device").map { it.getAttribute("id") }, keep, rules(ignores[id].orEmpty()), JSONObject()
                    .put("rescanIntervalS", folder.getAttribute("rescanIntervalS").toIntOrNull() ?: 3600)
                    .put("fsWatcherEnabled", folder.getAttribute("fsWatcherEnabled") != "false")
                    .put("ignorePerms", folder.getAttribute("ignorePerms") == "true"))
        }
        val options = JSONObject()
        root.children("options").singleOrNull()?.let { source ->
            listOf("maxSendKbps", "maxRecvKbps", "reconnectionIntervalS").forEach { name ->
                source.children(name).singleOrNull()?.textContent?.toIntOrNull()?.let { options.put(name, it.coerceAtLeast(0)) }
            }
            options.put("limitBandwidthInLan", source.children("limitBandwidthInLan").singleOrNull()?.textContent == "true")
        }
        return SyncthingImportModel(devices, folders, ownId, identity, options)
    }

    private fun portable(json: JSONObject): SyncthingImportModel {
        require(json.getString("format") == "WizeFilesSyncthing" && json.getInt("version") == 1) { "Unsupported Syncthing backup format" }
        val identity = json.optJSONObject("identity")?.let {
            SyncthingIdentity(Base64.getDecoder().decode(it.getString("certificate")), Base64.getDecoder().decode(it.getString("privateKey")))
        }
        val ownId = json.getString("ownId")
        require(identity == null || identity.validate() == ownId) { "Backup identity does not match its device ID" }
        val devices = json.getJSONArray("devices").objects().map(SyncthingDevice::decode)
        val folders = json.getJSONArray("folders").objects().map { folder ->
            val profile = folder.getJSONObject("profile")
            SyncthingImportedFolder(folder.getString("id"), profile.getString("name"),
                java.io.File(java.net.URI(profile.getString("sourceUri"))).path,
                SyncMode.valueOf(profile.getString("mode")), folder.getJSONArray("peers").strings(),
                SyncthingVersionPolicy.keep(profile.getString("protectionJson")),
                rules(folder.optJSONArray("ignores")?.strings().orEmpty()), folder.optJSONObject("options") ?: JSONObject(), profile)
        }
        return SyncthingImportModel(devices, folders, ownId, identity, json.optJSONObject("options") ?: JSONObject())
    }
    private fun Element.children(name: String): List<Element> = (0 until childNodes.length)
        .mapNotNull { childNodes.item(it) as? Element }.filter { it.tagName == name }

    private fun rules(lines: List<String>) = lines.filterNot {
        it == "// WizeFiles managed rules begin" || it == "// WizeFiles managed rules end"
    }
}
