// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import com.wisso.wizefiles.storage.NearbySessionState
import com.wisso.wizefiles.feature.transfer.TransferOperationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbySessionDomainControllerTest {
    @Test fun `authenticated transfer follows explicit production transitions`() {
        val controller = NearbySessionDomainController()
        controller.beginDiscovery()
        controller.peerFound()
        controller.authenticated()
        controller.transferStarted()
        controller.completed()
        assertEquals(NearbySessionState.COMPLETED, controller.state)
    }

    @Test fun `invalid peer cannot start transfer before authentication`() {
        val controller = NearbySessionDomainController()
        controller.beginDiscovery()
        assertFalse(controller.transferStarted())
        assertEquals(NearbySessionState.DISCOVERING, controller.state)
    }

    @Test fun `late callbacks after cancellation are controlled no ops`() {
        val controller = NearbySessionDomainController()
        controller.beginDiscovery()
        assertTrue(controller.cancelled())
        assertFalse(controller.peerFound())
        assertFalse(controller.failed(recoverable = true))
        assertFalse(controller.pause())
        assertEquals(NearbySessionState.CANCELLED, controller.state)
        assertEquals(TransferOperationState.CANCELLED, controller.projection().operationState)
    }

    @Test fun `authentication timeout and disconnect project authoritative recovery states`() {
        val controller = NearbySessionDomainController()
        controller.beginDiscovery()
        controller.peerFound()
        controller.failed(recoverable = true)
        assertEquals(NearbySessionState.FAILED, controller.state)
        assertEquals(TransferOperationState.FAILED, controller.projection().operationState)

        controller.reset()
        controller.beginDiscovery()
        controller.peerFound()
        controller.authenticated()
        controller.failed(recoverable = true)
        assertEquals(NearbyPhase.RECOVERABLE, controller.projection().phase)
        assertEquals(TransferOperationState.RECOVERABLE, controller.projection().operationState)
    }

    @Test fun `pause and resume remain explicit recoverable transitions`() {
        val controller = NearbySessionDomainController()
        controller.beginDiscovery()
        controller.peerFound()
        controller.authenticated()
        controller.transferStarted()
        controller.pause()
        assertEquals(NearbySessionState.PAUSED, controller.state)
        controller.resumeTransfer()
        assertEquals(NearbySessionState.TRANSFERRING, controller.state)
    }
}
