// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SyncthingImportParserTest {
    private val xml = """<configuration version="37"><device id="SELF" name="Phone"/><device id="PEER" name="Desktop"><address>tcp://192.168.1.2:22000</address></device>
        <folder id="photos" label="Photos" path="/storage/emulated/0/Pictures" type="sendonly" filesystemType="basic" rescanIntervalS="300" fsWatcherEnabled="true">
        <device id="SELF"/><device id="PEER"/><versioning type="simple"><param key="keep" val="12"/></versioning></folder>
        <options><maxSendKbps>2048</maxSendKbps><limitBandwidthInLan>true</limitBandwidthInLan></options></configuration>"""
    private fun parse(bytes: ByteArray) = SyncthingImportParser.parse(bytes, CharArray(0))
    @Test fun nativeXmlKeepsDevicesModesPathsSharingRetentionAndCompatibleOptions() {
        val model = parse(xml.toByteArray())
        assertEquals("", model.originalId) // The user must select the original device without a certificate.
        assertEquals("Desktop", model.devices.last().name)
        assertEquals(listOf("SELF", "PEER"), model.folders.single().peers)
        assertEquals(SyncMode.MIRROR, model.folders.single().mode)
        assertEquals(12, model.folders.single().keep)
        assertEquals(300, model.folders.single().folderOptions.getInt("rescanIntervalS"))
        assertEquals(2048, model.options.getInt("maxSendKbps"))
    }
    @Test fun xmlEntitiesExternalVersionersAndUnsupportedModesAreRejected() {
        listOf("<!DOCTYPE configuration [<!ENTITY x SYSTEM 'file:///secret'>]>$xml",
            xml.replace("sendonly", "receiveencrypted"), xml.replace("type=\"simple\"", "type=\"external\""),
            xml.replace("id=\"PEER\" name=\"Desktop\"", "id=\"UNKNOWN\" name=\"Desktop\"")).forEach {
            assertThrows(IllegalArgumentException::class.java) { parse(it.toByteArray()) }
        }
    }
    @Test fun archiveRejectsTraversalAndExpansionBombsAndReadsPortableIgnores() {
        fun zip(entries: Map<String, ByteArray>) = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { stream -> entries.forEach { (name, content) ->
                stream.putNextEntry(ZipEntry(name)); stream.write(content); stream.closeEntry()
            } }
        }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) { parse(zip(mapOf("../config.xml" to xml.toByteArray()))) }
        assertThrows(IllegalArgumentException::class.java) {
            parse(zip(mapOf("config.xml" to xml.toByteArray(), "padding" to ByteArray(SyncthingBackupCodec.MAX_BYTES + 1))))
        }
        val parsed = parse(zip(mapOf("export/config.xml" to xml.toByteArray(), "export/ignores/photos.stignore" to "*.tmp\n.cache".toByteArray())))
        assertEquals(listOf("*.tmp", ".cache"), parsed.folders.single().ignores)
    }
    @Test fun encryptedPortableBackupRetainsProfilePoliciesAndValidatedIdentity() {
        val identity = testSyncthingIdentity()
        val profile = SyncProfile(name = "Photos", sourceUri = "file:///storage/emulated/0/Pictures",
            destinationUri = "syncthing://PEER/photos", mode = SyncMode.TWO_WAY, propagateDeletions = true,
            scheduleJson = "{\"type\":\"INTERVAL\",\"intervalMinutes\":45}",
            constraintsJson = SyncthingSessionOptions(manualMinutes = 0, retryMinutes = 9).mergeInto("{}"))
        val json = JSONObject().put("format", "WizeFilesSyncthing").put("version", 1).put("ownId", identity.validate())
            .put("devices", JSONArray().put(SyncthingDevice("PEER", "Laptop").json()))
            .put("folders", JSONArray().put(JSONObject().put("id", "photos").put("profile", SyncthingProfileCodec.encode(profile))
                .put("peers", JSONArray().put("PEER")).put("ignores", JSONArray().put("*.tmp"))))
            .put("identity", JSONObject().put("certificate", java.util.Base64.getEncoder().encodeToString(identity.certificate))
                .put("privateKey", java.util.Base64.getEncoder().encodeToString(identity.privateKey)))
        val password = "a portable identity backup".toCharArray()
        val parsed = SyncthingImportParser.parse(SyncthingBackupCodec.encrypt(json.toString().toByteArray(), password), password)
        assertEquals(identity.validate(), parsed.identity!!.validate())
        val restored = parsed.folders.single().profile("/new/Pictures", parsed.originalId, parsed.originalId, false)
        assertEquals(profile.scheduleJson, restored.scheduleJson)
        assertEquals(0, SyncthingSessionOptions.decode(restored.constraintsJson).manualMinutes)
        assertEquals(9, SyncthingSessionOptions.decode(restored.constraintsJson).retryMinutes)
        assertEquals("file:/new/Pictures", restored.sourceUri)
    }
}
