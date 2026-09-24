package com.wisso.wizefiles.storagecleaner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val PREFERENCE_FILE_NAME = "storage_cleaner_preferences"
private const val KEY_PREFERENCES_JSON = "preferences_json"

data class IgnoredCleanupItem(
    val id: String,
    val type: RecommendationType,
    val title: String,
    val location: String?,
    val ignoredAtMillis: Long
)

data class StorageCleanerPreferences(
    val ignoredItems: List<IgnoredCleanupItem> = emptyList(),
    val duplicateKeepOverrides: Map<String, String> = emptyMap()
)

interface StorageCleanerPreferenceStoreApi {
    fun load(): StorageCleanerPreferences
    fun save(preferences: StorageCleanerPreferences)
    fun clear()
}

class StorageCleanerPreferenceStore(context: Context) : StorageCleanerPreferenceStoreApi {
    private val prefs = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): StorageCleanerPreferences {
        val raw = prefs.getString(KEY_PREFERENCES_JSON, null) ?: return StorageCleanerPreferences()
        return runCatching { StorageCleanerPreferenceJson.decode(raw) }
            .getOrDefault(StorageCleanerPreferences())
    }

    override fun save(preferences: StorageCleanerPreferences) {
        prefs.edit()
            .putString(KEY_PREFERENCES_JSON, StorageCleanerPreferenceJson.encode(preferences))
            .apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_PREFERENCES_JSON).apply()
    }
}

internal object StorageCleanerPreferenceJson {
    private const val SCHEMA_VERSION = 1

    fun encode(preferences: StorageCleanerPreferences): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("ignoredItems", JSONArray().apply {
            preferences.ignoredItems.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("type", item.type.name)
                    put("title", item.title)
                    put("location", item.location)
                    put("ignoredAtMillis", item.ignoredAtMillis)
                })
            }
        })
        put("duplicateKeepOverrides", JSONObject().apply {
            preferences.duplicateKeepOverrides.forEach { (groupId, path) ->
                put(groupId, path)
            }
        })
    }.toString()

    fun decode(raw: String): StorageCleanerPreferences {
        val root = JSONObject(raw)
        val ignored = root.optJSONArray("ignoredItems").toObjects().mapNotNull { item ->
            val type = runCatching {
                RecommendationType.valueOf(item.getString("type"))
            }.getOrNull() ?: return@mapNotNull null
            IgnoredCleanupItem(
                id = item.getString("id"),
                type = type,
                title = item.optString("title"),
                location = item.optString("location").takeIf { it.isNotBlank() },
                ignoredAtMillis = item.optLong("ignoredAtMillis")
            )
        }
        val overridesObject = root.optJSONObject("duplicateKeepOverrides")
        val overrides = buildMap {
            if (overridesObject != null) {
                val keys = overridesObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val path = overridesObject.optString(key)
                    if (path.isNotBlank()) put(key, path)
                }
            }
        }
        return StorageCleanerPreferences(ignored, overrides)
    }

    private fun JSONArray?.toObjects(): List<JSONObject> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.let(::add)
            }
        }
    }
}
