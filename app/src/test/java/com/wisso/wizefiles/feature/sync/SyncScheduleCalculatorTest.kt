package com.wisso.wizefiles.feature.sync

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncScheduleCalculatorTest {
    private val zone = ZoneId.of("UTC")

    @Test
    fun intervalRejectsLessThanWorkManagerMinimum() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncSchedule(type = SyncScheduleType.INTERVAL, intervalMinutes = 14)
        }
    }

    @Test
    fun dailyScheduleUsesNextDayAfterTimePassed() {
        val now = LocalDateTime.of(2026, 8, 2, 3, 0).atZone(zone).toInstant().toEpochMilli()
        val delay = SyncScheduleCalculator.nextDelay(
            SyncSchedule(type = SyncScheduleType.DAILY, localTime = LocalTime.of(2, 0)),
            now,
            zone
        )
        assertEquals(23 * 60, delay.toMinutes())
    }

    @Test
    fun weeklyScheduleSelectsConfiguredDay() {
        val now = LocalDateTime.of(2026, 8, 2, 1, 0).atZone(zone).toInstant().toEpochMilli()
        val delay = SyncScheduleCalculator.nextDelay(
            SyncSchedule(
                type = SyncScheduleType.WEEKLY,
                localTime = LocalTime.of(2, 0),
                daysOfWeek = setOf(DayOfWeek.MONDAY)
            ),
            now,
            zone
        )
        assertEquals(25 * 60, delay.toMinutes())
    }
}
