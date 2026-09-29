// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import org.json.JSONArray
import org.json.JSONObject

internal data class SyncthingDevice(
    val id: String,
    val name: String = "",
    val addresses: List<String> = listOf("dynamic")
) {
    init {
        require(id.isNotBlank() && id.length <= 128 && id.none(Char::isISOControl))
        require(name.length <= 128)
        require(addresses.isNotEmpty() && addresses.size <= 16)
        require(addresses.all { address ->
            address == "dynamic" || runCatching {
                val uri = java.net.URI(address)
                address.length <= 512 && uri.scheme in setOf("tcp", "quic", "relay") &&
                    !uri.host.isNullOrBlank() && uri.userInfo == null
            }.getOrDefault(false)
        }) { "Invalid Syncthing device address" }
    }
    fun json() = JSONObject().put("deviceID", id).put("name", name).put("addresses", JSONArray(addresses))

    companion object {
        fun decode(json: JSONObject) = SyncthingDevice(json.getString("deviceID"), json.optString("name"),
            json.optJSONArray("addresses")?.strings()?.ifEmpty { listOf("dynamic") } ?: listOf("dynamic"))
    }
}

/** Devices and all folder shares change in one atomic document. Empty shares stay empty. */
internal class SyncthingConfiguration(private val store: SyncthingJsonStore) {
    fun snapshot(): JSONObject = store.read()
    fun replace(value: JSONObject) = store.update { root ->
        root.keys().asSequence().toList().forEach(root::remove)
        value.keys().forEach { root.put(it, value.get(it)) }
    }
    fun options(): JSONObject = snapshot().optJSONObject("options") ?: JSONObject()
    fun devices(): List<SyncthingDevice> = snapshot().optJSONObject("devices")?.let { devices ->
        devices.keys().asSequence().map { SyncthingDevice.decode(devices.getJSONObject(it)) }.toList()
    }.orEmpty().sortedWith(compareBy({ it.name.lowercase() }, { it.id }))

    fun migrate(profileId: String, legacyPeer: String) = store.update { root ->
        val shares = root.objectOrCreate("shares")
        if (!shares.has(profileId)) {
            shares.put(profileId, JSONArray().put(legacyPeer))
            val devices = root.objectOrCreate("devices")
            if (!devices.has(legacyPeer)) devices.put(legacyPeer, SyncthingDevice(legacyPeer).json())
        }
    }

    fun peers(profileId: String): List<SyncthingDevice> {
        val root = snapshot()
        val ids = root.optJSONObject("shares")?.optJSONArray(profileId)?.strings().orEmpty()
        val devices = root.optJSONObject("devices") ?: return emptyList()
        return ids.mapNotNull { devices.optJSONObject(it)?.let(SyncthingDevice::decode) }
    }

    fun saveDevice(device: SyncthingDevice) = store.update {
        it.objectOrCreate("devices").put(device.id, device.json())
    }

    fun removeDevice(id: String) = store.update { root ->
        root.objectOrCreate("devices").remove(id)
        val shares = root.objectOrCreate("shares")
        shares.keys().asSequence().toList().forEach { profileId ->
            shares.put(profileId, JSONArray(shares.getJSONArray(profileId).strings().filter { it != id }))
        }
    }

    fun setPeers(profileId: String, ids: List<String>) = store.update { root ->
        val devices = root.objectOrCreate("devices")
        require(ids.all(devices::has)) { "Unknown Syncthing device" }
        root.objectOrCreate("shares").put(profileId, JSONArray(ids.distinct()))
    }

    fun isPaused(profileId: String): Boolean = snapshot().optJSONObject("paused")?.optBoolean(profileId) == true
    fun setPaused(profileId: String, paused: Boolean) = store.update {
        it.objectOrCreate("paused").put(profileId, paused)
    }
    fun ownId(): String = snapshot().optString("ownId")
    fun rememberOwnId(id: String) = store.update { it.put("ownId", id) }
}

internal fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)
internal fun JSONObject.objectOrCreate(key: String): JSONObject =
    optJSONObject(key) ?: JSONObject().also { put(key, it) }
