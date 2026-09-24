package com.wisso.wizefiles.util

fun AutoCloseable.closeSafe() {
    try {
        close()
    } catch (e: Exception) {
        com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
    }
}
