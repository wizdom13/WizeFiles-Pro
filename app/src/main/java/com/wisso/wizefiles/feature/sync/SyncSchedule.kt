package com.wisso.wizefiles.feature.sync

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

internal data class SyncSchedule(
    val type: SyncScheduleType = SyncScheduleType.MANUAL,
    val intervalMinutes: Long = 15,
    val localTime: LocalTime = LocalTime.of(2, 0),
    val daysOfWeek: Set<DayOfWeek> = setOf(DayOfWeek.SUNDAY),
    val chargingOnly: Boolean = false,
    val batteryNotLow: Boolean = true,
    val storageNotLow: Boolean = true,
    val unmeteredOnly: Boolean = false,
    val wifiOnly: Boolean = false,
    val runWhenConstraintsAvailable: Boolean = true
) {
    init {
        if (type == SyncScheduleType.INTERVAL) {
            require(intervalMinutes >= MINIMUM_INTERVAL_MINUTES)
        }
        if (type == SyncScheduleType.WEEKLY) require(daysOfWeek.isNotEmpty())
    }

    companion object {
        const val MINIMUM_INTERVAL_MINUTES = 15L
    }
}

internal object SyncScheduleCalculator {
    fun nextDelay(
        schedule: SyncSchedule,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Duration {
        require(schedule.type == SyncScheduleType.DAILY || schedule.type == SyncScheduleType.WEEKLY)
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        var candidate = now.toLocalDate().atTime(schedule.localTime).atZone(zoneId)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        if (schedule.type == SyncScheduleType.WEEKLY) {
            while (candidate.dayOfWeek !in schedule.daysOfWeek) candidate = candidate.plusDays(1)
        }
        return Duration.between(now, candidate).coerceAtLeast(Duration.ZERO)
    }
}
