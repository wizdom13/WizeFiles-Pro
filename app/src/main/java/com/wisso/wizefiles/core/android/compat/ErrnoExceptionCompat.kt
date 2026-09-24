package com.wisso.wizefiles.core.android.compat

import android.system.ErrnoException

/**
 * Returns the syscall name without reflecting into Android's hidden
 * ErrnoException.functionName field. Android 17 denies that reflection.
 */
val ErrnoException.functionNameCompat: String
    get() = functionNameFromErrnoMessage(message)

internal fun functionNameFromErrnoMessage(message: String?): String {
    val value = message.orEmpty()
    val markerIndex = value.indexOf(" failed:")
    return if (markerIndex > 0) value.substring(0, markerIndex) else "syscall"
}
