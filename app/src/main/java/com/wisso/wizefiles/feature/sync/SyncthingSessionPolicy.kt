// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import java.util.concurrent.TimeUnit
import org.json.JSONObject

internal data class SyncthingSessionOptions(
    val manualMinutes: Int = 30,
    val backgroundMinutes: Int = 8,
    val automaticRetry: Boolean = true,
    val retryMinutes: Int = 5,
    val retryLimit: Int = 6
) {
    init {
        require(manualMinutes in 0..360)
        require(backgroundMinutes in 1..8)
        require(retryMinutes in 1..300)
        require(retryLimit in 0..100)
    }

    /** Zero means until complete, while Android still permits foreground execution. */
    fun durationMillis(foreground: Boolean): Long = when {
        !foreground -> TimeUnit.MINUTES.toMillis(backgroundMinutes.toLong())
        manualMinutes == 0 -> Long.MAX_VALUE
        else -> TimeUnit.MINUTES.toMillis(manualMinutes.toLong())
    }

    fun backoffMillis(attempt: Int): Long = minOf(TimeUnit.HOURS.toMillis(5),
        TimeUnit.MINUTES.toMillis(retryMinutes.toLong()) * (attempt.coerceAtLeast(0).toLong() + 1))

    fun shouldRetry(attempt: Int): Boolean = automaticRetry && attempt < retryLimit

    fun mergeInto(constraints: String): String = JSONObject(constraints).apply {
        put("syncthingSession", JSONObject().apply {
            put("manualMinutes", manualMinutes)
            put("backgroundMinutes", backgroundMinutes)
            put("automaticRetry", automaticRetry)
            put("retryMinutes", retryMinutes)
            put("retryLimit", retryLimit)
        })
    }.toString()

    companion object {
        fun decode(constraints: String): SyncthingSessionOptions {
            val json = runCatching { JSONObject(constraints).optJSONObject("syncthingSession") }
                .getOrNull() ?: return SyncthingSessionOptions()
            return SyncthingSessionOptions(
                json.optInt("manualMinutes", 30).coerceIn(0, 360),
                json.optInt("backgroundMinutes", 8).coerceIn(1, 8),
                json.optBoolean("automaticRetry", true),
                json.optInt("retryMinutes", 5).coerceIn(1, 300),
                json.optInt("retryLimit", 6).coerceIn(0, 100)
            )
        }
    }
}

internal enum class SyncthingSessionOutcome {
    RUNNING, COMPLETED, PENDING, WAITING_FOR_PEER, INTERRUPTED, PAUSED, CANCELLED, FAILED
}

internal object SyncthingSessionPolicy {
    fun expired(startNanos: Long, nowNanos: Long, durationMillis: Long): Boolean =
        durationMillis != Long.MAX_VALUE && nowNanos - startNanos >=
            TimeUnit.MILLISECONDS.toNanos(durationMillis)

    fun unfinished(disconnected: Boolean) = if (disconnected) {
        SyncthingSessionOutcome.WAITING_FOR_PEER
    } else SyncthingSessionOutcome.PENDING
}
