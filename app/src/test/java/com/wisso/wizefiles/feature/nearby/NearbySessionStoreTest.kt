// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wisso.wizefiles.storage.NearbyPayloadDirection
import com.wisso.wizefiles.storage.NearbyPayloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NearbySessionStoreTest {
    private lateinit var context: Context
    private lateinit var preferences: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    @Test
    fun `exact legacy v1 snapshot remains readable`() {
        val legacy = """{"operationId":"legacy-operation","sessionId":"legacy-session","role":"RECEIVE","peerName":"peer","destinationUri":"file:///target","conflictPolicy":"KEEP_BOTH","updatedAtMillis":1234}"""
        preferences.edit().putString("legacy-operation", legacy).commit()

        val restored = requireNotNull(NearbySessionStore(context).load("legacy-operation"))

        assertEquals("legacy-session", restored.sessionId)
        assertEquals(NearbyRole.RECEIVE, restored.role)
        assertEquals(1234, restored.updatedAtMillis)
        assertTrue(restored.payloadCheckpoints.isEmpty())
    }

    @Test
    fun `version 2 payload checkpoints round trip without transport objects`() {
        val encoded = """{"version":2,"operationId":"operation","sessionId":"session","role":"SEND","peerName":"peer","destinationUri":"file:///target","conflictPolicy":"KEEP_BOTH","updatedAtMillis":1234,"payloadCheckpoints":[{"id":7,"direction":"INCOMING","state":"READY"},{"id":8,"direction":"OUTGOING","state":"ACTIVE"}]}"""
        preferences.edit().putString("operation", encoded).commit()

        val restored = requireNotNull(NearbySessionStore(context).load("operation"))

        assertEquals(listOf(7L, 8L), restored.payloadCheckpoints.map { it.payloadId })
        assertEquals(listOf(NearbyPayloadDirection.INCOMING, NearbyPayloadDirection.OUTGOING), restored.payloadCheckpoints.map { it.direction })
        assertEquals(listOf(NearbyPayloadState.READY, NearbyPayloadState.ACTIVE), restored.payloadCheckpoints.map { it.state })
    }

    @Test
    fun `corrupt snapshot is removed after the first failed load`() {
        preferences.edit().putString("corrupt", "{not-json").commit()

        assertNull(NearbySessionStore(context).load("corrupt"))
        assertFalse(preferences.contains("corrupt"))
        assertNull(NearbySessionStore(context).load("corrupt"))
    }

    @Test
    fun `oversized checkpoint array is rejected and removed`() {
        val checkpoints = (0..64).joinToString(",") {
            """{"id":$it,"direction":"INCOMING","state":"PENDING"}"""
        }
        val encoded = """{"version":2,"operationId":"oversized","sessionId":"session","role":"RECEIVE","destinationUri":"file:///target","payloadCheckpoints":[$checkpoints]}"""
        preferences.edit().putString("oversized", encoded).commit()

        assertNull(NearbySessionStore(context).load("oversized"))
        assertFalse(preferences.contains("oversized"))
    }

    private companion object {
        const val PREFERENCES = "nearby_transfer_sessions_v1"
    }
}
