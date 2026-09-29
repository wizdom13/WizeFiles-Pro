// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import org.json.JSONArray
import org.json.JSONObject

internal data class BrowserTabSnapshot(
    val title: String,
    val primaryLocation: BrowserTabLocation?,
    val secondaryLocation: BrowserTabLocation? = null,
    val dualPaneEnabled: Boolean = false,
    val activePane: BrowserPane = BrowserPane.PRIMARY,
    val dividerFraction: Float = BrowserTabState.DEFAULT_DIVIDER_FRACTION
)

internal data class BrowserTabSession(val tabs: List<BrowserTabSnapshot>, val activeIndex: Int)

/** Disk format contains folder references, never provider objects, grants or credentials. */
internal class BrowserTabSessionStore(
    private val read: () -> String?,
    private val write: (String) -> Unit
) {
    fun load(): BrowserTabSession? = runCatching {
        val json = JSONObject(read() ?: return null)
        if (json.getInt("version") != 1) return null
        val entries = json.getJSONArray("tabs")
        val tabs = (0 until minOf(entries.length(), BrowserTabsController.MAXIMUM_TAB_COUNT)).map {
            val tab = entries.getJSONObject(it)
            BrowserTabSnapshot(
                title = tab.getString("title"),
                primaryLocation = decodeLocation(tab.optJSONObject("primary")),
                secondaryLocation = decodeLocation(tab.optJSONObject("secondary")),
                dualPaneEnabled = tab.optBoolean("dualPane"),
                activePane = BrowserPane.entries.getOrElse(tab.optInt("activePane")) {
                    BrowserPane.PRIMARY
                },
                dividerFraction = tab.optDouble("divider", 0.5).toFloat()
                    .takeIf { it.isFinite() }
                    ?.coerceIn(
                        BrowserTabsController.MINIMUM_DIVIDER_FRACTION,
                        BrowserTabsController.MAXIMUM_DIVIDER_FRACTION
                    ) ?: BrowserTabState.DEFAULT_DIVIDER_FRACTION
            )
        }
        if (tabs.isEmpty()) return null
        BrowserTabSession(tabs, json.optInt("activeIndex").coerceIn(tabs.indices))
    }.getOrNull()

    fun save(session: BrowserTabSession) {
        if (session.tabs.isEmpty()) return
        val tabs = session.tabs.take(BrowserTabsController.MAXIMUM_TAB_COUNT)
        val entries = JSONArray()
        tabs.forEach { tab ->
            entries.put(JSONObject().apply {
                put("title", tab.title)
                put("primary", encodeLocation(tab.primaryLocation))
                put("secondary", encodeLocation(tab.secondaryLocation))
                put("dualPane", tab.dualPaneEnabled)
                put("activePane", tab.activePane.ordinal)
                put("divider", tab.dividerFraction.toDouble())
            })
        }
        write(JSONObject().apply {
            put("version", 1)
            put("tabs", entries)
            put("activeIndex", session.activeIndex.coerceIn(tabs.indices))
        }.toString())
    }

    private fun encodeLocation(location: BrowserTabLocation?): Any =
        location?.let {
            JSONObject().apply {
                put("path", it.path)
                it.storageId?.let { id -> put("storageId", id) }
            }
        } ?: JSONObject.NULL

    private fun decodeLocation(json: JSONObject?): BrowserTabLocation? = json?.let {
        BrowserTabLocation(
            path = it.getString("path"),
            storageId = if (it.has("storageId")) it.getLong("storageId") else null
        )
    }
}
