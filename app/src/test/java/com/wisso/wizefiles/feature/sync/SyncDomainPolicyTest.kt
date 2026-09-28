// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import com.wisso.wizefiles.storage.SyncDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncDomainPolicyTest {
    @Test fun `same provider distinct endpoints remain valid`() {
        val plan = SyncDomainPolicy.validate(profile("file:///source", "file:///destination"))
        assertEquals(SyncDirection.PUSH, plan.direction)
    }

    @Test fun `identical endpoints are rejected before scanning`() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncDomainPolicy.validate(profile("file:///same", "file:///same"))
        }
    }

    @Test fun `baseline tracked two way deletion is not treated as mirror deletion`() {
        val plan = SyncDomainPolicy.validate(
            profile("file:///a", "file:///b").copy(
                mode = SyncMode.TWO_WAY,
                propagateDeletions = true
            )
        )
        assertEquals(false, plan.deleteExtraneous)
    }

    @Test fun `syncthing endpoint round trips device and folder ids`() {
        val endpoint = SyncthingEndpoint(
            deviceId = "DEVICE-ID-123",
            folderId = "Photos Shared"
        )

        assertEquals(
            endpoint,
            SyncthingEndpointCodec.decode(SyncthingEndpointCodec.encode(endpoint))
        )
    }

    @Test fun `syncthing mirror uses send only and update only is rejected`() {
        val destination = SyncthingEndpointCodec.encode(
            SyncthingEndpoint("DEVICE-ID-123", "documents")
        )

        assertThrows(IllegalArgumentException::class.java) {
            SyncthingProfilePolicy.folderMode(profile("file:///documents", destination))
        }
        assertEquals(
            SyncthingFolderMode.SEND_ONLY,
            SyncthingProfilePolicy.folderMode(
                profile("file:///documents", destination).copy(mode = SyncMode.MIRROR, propagateDeletions = true)
            )
        )
    }

    @Test fun `syncthing two way profile uses send receive mode`() {
        val destination = SyncthingEndpointCodec.encode(
            SyncthingEndpoint("DEVICE-ID-123", "documents")
        )

        val profile = profile("file:///documents", destination).copy(mode = SyncMode.TWO_WAY, propagateDeletions = true)

        assertEquals(SyncthingFolderMode.SEND_RECEIVE, SyncthingProfilePolicy.folderMode(profile))
        assertEquals(SyncDirection.TWO_WAY, SyncDomainPolicy.validate(profile).direction)
    }

    @Test fun `syncthing rejects move source mode`() {
        val destination = SyncthingEndpointCodec.encode(
            SyncthingEndpoint("DEVICE-ID-123", "documents")
        )

        assertThrows(IllegalArgumentException::class.java) {
            SyncDomainPolicy.validate(
                profile("file:///documents", destination).copy(mode = SyncMode.MOVE_SOURCE)
            )
        }
    }

    @Test fun `malformed syncthing destination is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncDomainPolicy.validate(
                profile("file:///documents", "syncthing://device/one/two")
            )
        }
    }

    private fun profile(source: String, destination: String) = SyncProfile(
        name = "test",
        sourceUri = source,
        destinationUri = destination,
        mode = SyncMode.UPDATE_DESTINATION
    )
}

