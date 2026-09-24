// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretReferenceCodecTest {

    @Test
    fun `generated references are flagged as references`() {
        val reference = SecretReferenceCodec.createReference()

        assertTrue(SecretReferenceCodec.isReference(reference))
    }

    @Test
    fun `plain values are not treated as references`() {
        assertFalse(SecretReferenceCodec.isReference("password"))
    }
}
