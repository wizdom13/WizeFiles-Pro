// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import java.time.DayOfWeek
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject

internal object SyncScheduleCodec {
    fun encode(schedule: SyncSchedule): String = JSONObject()
        .put("type", schedule.type.name)
        .put("intervalMinutes", schedule.intervalMinutes)
        .put("hour", schedule.localTime.hour)
        .put("minute", schedule.localTime.minute)
        .put("days", JSONArray(schedule.daysOfWeek.map { it.name }))
        .put("chargingOnly", schedule.chargingOnly)
        .put("batteryNotLow", schedule.batteryNotLow)
        .put("storageNotLow", schedule.storageNotLow)
        .put("unmeteredOnly", schedule.unmeteredOnly)
        .put("wifiOnly", schedule.wifiOnly)
        .put("runWhenConstraintsAvailable", schedule.runWhenConstraintsAvailable)
        .toString()

    fun decode(value: String): SyncSchedule = runCatching {
        val json = JSONObject(value)
        val daysJson = json.optJSONArray("days") ?: JSONArray()
        val days = buildSet {
            repeat(daysJson.length()) { index ->
                runCatching { DayOfWeek.valueOf(daysJson.getString(index)) }.getOrNull()?.let(::add)
            }
        }
        SyncSchedule(
            type = SyncScheduleType.valueOf(json.optString("type", SyncScheduleType.MANUAL.name)),
            intervalMinutes = json.optLong("intervalMinutes", 15).coerceAtLeast(15),
            localTime = LocalTime.of(json.optInt("hour", 2), json.optInt("minute", 0)),
            daysOfWeek = days.ifEmpty { setOf(DayOfWeek.SUNDAY) },
            chargingOnly = json.optBoolean("chargingOnly", false),
            batteryNotLow = json.optBoolean("batteryNotLow", true),
            storageNotLow = json.optBoolean("storageNotLow", true),
            unmeteredOnly = json.optBoolean("unmeteredOnly", false),
            wifiOnly = json.optBoolean("wifiOnly", false),
            runWhenConstraintsAvailable = json.optBoolean("runWhenConstraintsAvailable", true)
        )
    }.getOrElse { SyncSchedule() }
}
