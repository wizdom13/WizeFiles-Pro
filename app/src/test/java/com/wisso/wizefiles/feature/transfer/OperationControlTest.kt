package com.wisso.wizefiles.feature.transfer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationControlTest {
    @Test
    fun `pause is cooperative and only affects attached operation`() {
        OperationControlRegistry.attach("first")
        OperationControlRegistry.attach("second")
        assertTrue(OperationControlRegistry.requestPause("first"))
        assertFalse(runCatching { OperationControlRegistry.throwIfPauseRequested("first") }.isSuccess)
        assertTrue(runCatching { OperationControlRegistry.throwIfPauseRequested("second") }.isSuccess)
        OperationControlRegistry.detach("first")
        OperationControlRegistry.detach("second")
    }
}
