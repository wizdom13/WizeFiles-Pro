// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.ui

import com.wisso.wizefiles.storage.path.LocalAppPath
import com.wisso.wizefiles.storage.path.RawAppPath
import java.io.File
import org.junit.Assert.assertSame
import org.junit.Test

class PreferenceFragmentCompatTest {

    @Test
    fun toOpenDirectoryLaunchInputReturnsOriginalAppPathForLocalPath() {
        val path = LocalAppPath(File("/storage/emulated/0/Download"))

        val result = path.toOpenDirectoryLaunchInput()

        assertSame(path, result)
    }

    @Test
    fun toOpenDirectoryLaunchInputReturnsOriginalAppPathForRawPath() {
        val path = RawAppPath("content://com.android.externalstorage.documents/tree/primary%3ADownload")

        val result = path.toOpenDirectoryLaunchInput()

        assertSame(path, result)
    }
}
