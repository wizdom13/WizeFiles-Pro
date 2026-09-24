package com.wisso.wizefiles.util

import android.os.Handler
import android.os.SystemClock

class ThrottledRunnable(
    private val handler: Handler,
    private val intervalMillis: Long,
    block: () -> Unit
) : () -> Unit {
    private val task = Runnable(block)
    private var reservedSlot = NO_SLOT

    @Synchronized
    override operator fun invoke() {
        val now = SystemClock.uptimeMillis()
        discardExpiredSchedule(now)

        val target = when {
            reservedSlot == NO_SLOT -> now
            reservedSlot <= now -> reservedSlot + intervalMillis
            else -> return
        }
        reservedSlot = target
        if (target <= now) {
            handler.post(task)
        } else {
            handler.postAtTime(task, target)
        }
    }

    @Synchronized
    fun cancel() {
        reservedSlot = NO_SLOT
        handler.removeCallbacks(task)
    }

    private fun discardExpiredSchedule(now: Long) {
        if (reservedSlot != NO_SLOT && now > reservedSlot + intervalMillis) {
            reservedSlot = NO_SLOT
        }
    }

    private companion object {
        const val NO_SLOT = Long.MIN_VALUE
    }
}
