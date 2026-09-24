// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class ApkSignVerifyViewModelTest {
    @Test
    fun `switching mode clears incompatible selections without retaining secrets`() {
        val state = ApkSignVerifyViewModel().apply {
            mode = ApkSignVerifyMode.SIGN
            pendingGeneration = true
        }

        state.switchMode(ApkSignVerifyMode.VERIFY)

        assertEquals(ApkSignVerifyMode.VERIFY, state.mode)
        assertNull(state.output)
        assertNull(state.keyStore)
        assertNull(state.detachedV4)
        assertFalse(state.pendingGeneration)
    }
}
