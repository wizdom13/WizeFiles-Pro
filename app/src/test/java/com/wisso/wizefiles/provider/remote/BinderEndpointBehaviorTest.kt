package com.wisso.wizefiles.provider.remote

import android.os.Binder
import android.os.IInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BinderEndpointBehaviorTest {
    @Test
    fun endpointConnectsLazilyAndCachesTheService() {
        var connections = 0
        val service = TestInterface()
        val endpoint = BinderEndpoint<IInterface> {
            connections++
            service
        }

        assertFalse(endpoint.isConnected())
        assertSame(service, endpoint.requireService())
        assertSame(service, endpoint.requireService())
        assertTrue(endpoint.isConnected())
        assertEquals(1, connections)
    }

    @Test
    fun deathListenersUseSnapshotsAndCanBeRemoved() {
        val endpoint = BinderEndpoint<IInterface> { TestInterface() }
        var firstCalls = 0
        var removedCalls = 0
        endpoint.onBinderDeath { firstCalls++ }
        val removed = endpoint.onBinderDeath { removedCalls++ }
        removed.close()

        endpoint.handleBinderDeath()

        assertEquals(1, firstCalls)
        assertEquals(0, removedCalls)
    }

    @Test
    fun closeClearsConnectionAndListeners() {
        val endpoint = BinderEndpoint<IInterface> { TestInterface() }
        var calls = 0
        endpoint.onBinderDeath { calls++ }
        endpoint.requireService()

        endpoint.close()
        endpoint.handleBinderDeath()

        assertFalse(endpoint.isConnected())
        assertEquals(0, calls)
    }

    private class TestInterface : Binder(), IInterface {
        override fun asBinder(): Binder = this
    }
}
