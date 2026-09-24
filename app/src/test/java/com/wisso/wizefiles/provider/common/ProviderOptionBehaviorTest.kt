// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import java.nio.channels.Channel
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderOptionBehaviorTest {

    @Test
    fun accessModesRepresentEachRequestedCapability() {
        val modes = arrayOf(AccessMode.WRITE, AccessMode.READ, AccessMode.WRITE).toAccessModes()

        assertTrue(modes.read)
        assertTrue(modes.write)
        assertFalse(modes.execute)
    }

    @Test
    fun linkOptionsRoundTripNoFollowFlag() {
        assertArrayEquals(
            arrayOf(LinkOption.NOFOLLOW_LINKS),
            arrayOf(LinkOption.NOFOLLOW_LINKS).toLinkOptions().toArray()
        )
        assertTrue(emptyArray<LinkOption>().toLinkOptions().toArray().isEmpty())
    }

    @Test
    fun openOptionsApplyNioDefaultsAndImplications() {
        val defaults = emptySet<OpenOption>().toOpenOptions()
        assertTrue(defaults.read)
        assertFalse(defaults.write)

        val append = setOf<OpenOption>(StandardOpenOption.APPEND).toOpenOptions()
        assertTrue(append.write)
        assertTrue(append.append)

        val delete = setOf<OpenOption>(StandardOpenOption.DELETE_ON_CLOSE).toOpenOptions()
        assertTrue(delete.noFollowLinks)
    }

    @Test
    fun openOptionsRejectInvalidAndUnknownCombinations() {
        assertThrows(IllegalStateException::class.java) {
            setOf<OpenOption>(
                StandardOpenOption.READ,
                StandardOpenOption.APPEND
            ).toOpenOptions()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            setOf<OpenOption>(UnknownOpenOption).toOpenOptions()
        }
    }

    @Test
    fun copyOptionsRoundTripStandardAndProgressOptions() {
        var lastProgress = -1L
        val parsed = arrayOf<CopyOption>(
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.COPY_ATTRIBUTES,
            LinkOption.NOFOLLOW_LINKS,
            ProgressCopyOption(25) { lastProgress = it }
        ).toCopyOptions()

        assertTrue(parsed.replaceExisting)
        assertTrue(parsed.copyAttributes)
        assertTrue(parsed.noFollowLinks)
        assertEquals(25L, parsed.progressIntervalMillis)

        parsed.progressListener!!.invoke(42)
        assertEquals(42L, lastProgress)

        val roundTrip = parsed.toArray().toCopyOptions()
        assertTrue(roundTrip.replaceExisting)
        assertTrue(roundTrip.copyAttributes)
        assertTrue(roundTrip.noFollowLinks)
        assertEquals(25L, roundTrip.progressIntervalMillis)
    }

    @Test
    fun copyOptionsRejectUnknownValues() {
        assertThrows(UnsupportedOperationException::class.java) {
            arrayOf<CopyOption>(UnknownCopyOption).toCopyOptions()
        }
    }

    @Test
    fun authorityHasStableDisplayAndEncodedForms() {
        val authority = UriAuthority("user name", "example.com", 2222)

        assertEquals("user name@example.com:2222", authority.toString())
        assertEquals("user%20name@example.com:2222", authority.encode())
        assertEquals("", UriAuthority.EMPTY.encode())
    }

    @Test
    fun forceableChannelDelegatesForceRequests() {
        val channel = RecordingChannel()

        assertTrue(channel.isForceable)
        channel.force(metaData = true)

        assertTrue(channel.lastMetaData)
    }

    private object UnknownOpenOption : OpenOption
    private object UnknownCopyOption : CopyOption

    private class RecordingChannel : Channel, ForceableChannel {
        var lastMetaData = false
        private var open = true

        override fun force(metaData: Boolean) {
            lastMetaData = metaData
        }

        override fun isOpen(): Boolean = open

        override fun close() {
            open = false
        }
    }
}
