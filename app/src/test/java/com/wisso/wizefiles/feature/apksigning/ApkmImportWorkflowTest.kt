package com.wisso.wizefiles.feature.apksigning

import org.junit.Assert.assertThrows
import org.junit.Test

class ApkmImportWorkflowTest {
    @Test
    fun `APKM import never overwrites its source`() {
        assertThrows(IllegalArgumentException::class.java) {
            ApkmImportWorkflowSpec(
                sourceUri = "file:///same.apkm",
                outputUri = "file:///same.apkm"
            )
        }
    }
}
