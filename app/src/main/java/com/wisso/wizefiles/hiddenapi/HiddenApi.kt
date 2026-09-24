package com.wisso.wizefiles.hiddenapi

import android.os.Build

object HiddenApi {
    fun disableHiddenApiChecks() =
        disableHiddenApiChecksForSdk(Build.VERSION.SDK_INT, System::loadLibrary)

    internal fun disableHiddenApiChecksForSdk(
        sdkInt: Int,
        loadLibrary: (String) -> Unit
    ) {
        if (sdkInt >= Build.VERSION_CODES.P) {
            loadLibrary("hiddenapi")
        }
    }
}
