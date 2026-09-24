// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileItemMenuVisibilityPolicyTest {

    @Test
    fun folder_doesNotExposeOpenWith() {
        assertFalse(
            FileItemMenuVisibilityPolicy.shouldShowOpenWith(
                isDirectory = true,
                isInRecycleBin = false
            )
        )
    }

    @Test
    fun folder_doesNotExposeShare() {
        assertFalse(
            FileItemMenuVisibilityPolicy.shouldShowShare(
                isDirectory = true,
                isInRecycleBin = false
            )
        )
    }

    @Test
    fun folder_doesNotExposeOpenInTerminalLauncher() {
        assertFalse(
            FileItemMenuVisibilityPolicy.shouldShowOpenInTerminalLauncher(
                isDirectory = true,
                isInRecycleBin = false
            )
        )
    }

    @Test
    fun regularFile_stillExposesFileOnlyActions() {
        assertTrue(
            FileItemMenuVisibilityPolicy.shouldShowOpenWith(
                isDirectory = false,
                isInRecycleBin = false
            )
        )
        assertTrue(
            FileItemMenuVisibilityPolicy.shouldShowShare(
                isDirectory = false,
                isInRecycleBin = false
            )
        )
        assertTrue(
            FileItemMenuVisibilityPolicy.shouldShowOpenInTerminalLauncher(
                isDirectory = false,
                isInRecycleBin = false
            )
        )
    }
}
