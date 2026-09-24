package com.wisso.wizefiles.feature.apksigning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkSignatureSelectionTest {
    @Test
    fun `v4 requires v2 or v3`() {
        assertThrows(IllegalArgumentException::class.java) {
            ApkSignatureSelection.of(ApkSignatureScheme.V4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ApkSignatureSelection.of(ApkSignatureScheme.V1, ApkSignatureScheme.V4)
        }
    }

    @Test
    fun `at least one embedded scheme is required`() {
        assertThrows(IllegalArgumentException::class.java) {
            ApkSignatureSelection.of(emptySet())
        }
    }

    @Test
    fun `valid selections preserve exact schemes`() {
        val selection = ApkSignatureSelection.of(
            ApkSignatureScheme.V1,
            ApkSignatureScheme.V2,
            ApkSignatureScheme.V3,
            ApkSignatureScheme.V4
        )

        assertEquals(ApkSignatureScheme.entries.toSet(), selection.schemes)
        assertTrue(selection.v1Enabled)
        assertTrue(selection.v2Enabled)
        assertTrue(selection.v3Enabled)
        assertTrue(selection.v4Enabled)
        assertFalse(ApkSignatureSelection.of(ApkSignatureScheme.V2).v4Enabled)
    }
}
