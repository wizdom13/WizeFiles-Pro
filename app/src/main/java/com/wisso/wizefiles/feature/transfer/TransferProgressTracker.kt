package com.wisso.wizefiles.feature.transfer

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

internal data class TransferSpeedSnapshot(
    val bytesPerSecond: Long,
    val etaSeconds: Long
)

internal class TransferProgressTracker(
    private val elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime
) {
    private data class Sample(
        val atMillis: Long,
        val transferredBytes: Long,
        val smoothedBytesPerSecond: Double
    )

    private val samples = ConcurrentHashMap<String, Sample>()

    fun sample(operationId: String, transferredBytes: Long, totalBytes: Long): TransferSpeedSnapshot {
        val now = elapsedRealtimeMillis()
        val previous = samples[operationId]
        if (previous == null || now <= previous.atMillis || transferredBytes < previous.transferredBytes) {
            samples[operationId] = Sample(now, transferredBytes, 0.0)
            return TransferSpeedSnapshot(0, -1)
        }
        val elapsedSeconds = (now - previous.atMillis) / 1_000.0
        if (elapsedSeconds < MINIMUM_SAMPLE_SECONDS) {
            return snapshot(previous.smoothedBytesPerSecond, transferredBytes, totalBytes)
        }
        val instantaneous = (transferredBytes - previous.transferredBytes) / elapsedSeconds
        val smoothed = if (previous.smoothedBytesPerSecond <= 0.0) {
            instantaneous
        } else {
            previous.smoothedBytesPerSecond * (1.0 - ALPHA) + instantaneous * ALPHA
        }.coerceAtLeast(0.0)
        samples[operationId] = Sample(now, transferredBytes, smoothed)
        return snapshot(smoothed, transferredBytes, totalBytes)
    }

    fun reset(operationId: String) {
        samples.remove(operationId)
    }

    private fun snapshot(speed: Double, transferredBytes: Long, totalBytes: Long): TransferSpeedSnapshot {
        val eta = if (speed > 0.0 && totalBytes > transferredBytes) {
            ((totalBytes - transferredBytes) / speed).toLong().coerceAtLeast(0)
        } else {
            -1
        }
        return TransferSpeedSnapshot(speed.toLong().coerceAtLeast(0), eta)
    }

    companion object {
        private const val ALPHA = 0.25
        private const val MINIMUM_SAMPLE_SECONDS = 0.2
    }
}

internal object TransferProgress {
    val tracker = TransferProgressTracker()
}
