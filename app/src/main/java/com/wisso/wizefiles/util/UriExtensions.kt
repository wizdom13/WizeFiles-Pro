package com.wisso.wizefiles.util

import android.net.Uri
import com.wisso.wizefiles.core.app.contentResolver

fun Uri.takePersistablePermission(modeFlags: Int): Boolean =
    try {
        contentResolver.takePersistableUriPermission(this, modeFlags)
        true
    } catch (e: SecurityException) {
        com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
        false
    }

fun Uri.releasePersistablePermission(modeFlags: Int): Boolean =
    try {
        contentResolver.releasePersistableUriPermission(this, modeFlags)
        true
    } catch (e: SecurityException) {
        com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
        false
    }
