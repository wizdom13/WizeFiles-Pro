package com.wisso.wizefiles.provider.common

import java.nio.file.AccessMode

class AccessModes(
    val read: Boolean,
    val write: Boolean,
    val execute: Boolean
)

fun Array<out AccessMode>.toAccessModes(): AccessModes {
    val requested = asSequence().toSet()
    return AccessModes(
        read = AccessMode.READ in requested,
        write = AccessMode.WRITE in requested,
        execute = AccessMode.EXECUTE in requested
    )
}
