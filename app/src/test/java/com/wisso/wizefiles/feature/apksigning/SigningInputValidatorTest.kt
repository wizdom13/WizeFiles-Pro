// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SigningInputValidatorTest {
    @Test
    fun `valid selection exposes parsed minimum sdk`() {
        val result = SigningInputValidator.validate(
            setOf(ApkSignatureScheme.V2),
            "30"
        )

        assertEquals(30, (result as SigningInputValidation.Valid).minSdk)
    }

    @Test
    fun `verification rejects non-positive minimum sdk`() {
        assertTrue(runCatching { SigningInputValidator.verificationMinimumSdk("0") }.isFailure)
    }
}
