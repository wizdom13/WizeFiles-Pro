package com.wisso.wizefiles.util

import android.os.Handler

class DebouncedRunnable(
    private val handler: Handler,
    private val intervalMillis: Long,
    block: () -> Unit
) : () -> Unit {
    private val task = Runnable(block)

    @Synchronized
    override operator fun invoke() {
        handler.removeCallbacks(task)
        handler.postDelayed(task, intervalMillis)
    }

    @Synchronized
    fun cancel() {
        handler.removeCallbacks(task)
    }
}
