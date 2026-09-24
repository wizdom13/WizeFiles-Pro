package com.wisso.wizefiles.feature.advancedformats.sandbox

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** A bounded operation request. File paths, credentials, and passwords never cross this boundary. */
@Parcelize
data class FormatSandboxRequest(
    val operation: Int,
    val maxInputBytes: Long,
    val maxOutputBytes: Long,
    val timeoutMillis: Long
) : Parcelable {
    internal fun validationError(): String? = when {
        operation != OPERATION_COPY_BOUNDED -> "Unsupported sandbox operation"
        maxInputBytes !in 1..MAX_TRANSFER_BYTES -> "Invalid input limit"
        maxOutputBytes !in 1..MAX_TRANSFER_BYTES -> "Invalid output limit"
        timeoutMillis !in MIN_TIMEOUT_MILLIS..MAX_TIMEOUT_MILLIS -> "Invalid timeout"
        else -> null
    }

    companion object {
        const val OPERATION_COPY_BOUNDED = 1
        const val DEFAULT_TIMEOUT_MILLIS = 30_000L
        const val MIN_TIMEOUT_MILLIS = 100L
        const val MAX_TIMEOUT_MILLIS = 5 * 60_000L
        const val MAX_TRANSFER_BYTES = 2L * 1024L * 1024L * 1024L
    }
}

@Parcelize
data class FormatSandboxResult(
    val requestId: Long,
    val inputBytes: Long,
    val outputBytes: Long
) : Parcelable

object FormatSandboxError {
    const val INVALID_REQUEST = 1
    const val IO = 2
    const val TIMED_OUT = 3
    const val INTERNAL = 4
}
