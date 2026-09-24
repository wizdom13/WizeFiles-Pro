// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.sftp.client

import android.os.Build
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

object SecurityProviderHelper {
    @Synchronized
    fun init() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return

        val replacement = BouncyCastleProvider()
        val installed = Security.getProvider(replacement.name)
        if (installed?.javaClass == replacement.javaClass) return

        if (installed != null) Security.removeProvider(installed.name)
        Security.addProvider(replacement)
    }
}
