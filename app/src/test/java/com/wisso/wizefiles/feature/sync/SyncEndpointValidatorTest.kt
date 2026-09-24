package com.wisso.wizefiles.feature.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEndpointValidatorTest {
    @Test
    fun rejectsNestedRootsOnTheSameStorage() {
        val result = SyncEndpointValidator.validate(
            SyncEndpoint("file:///storage/docs", "internal", writable = true, available = true),
            SyncEndpoint("file:///storage/docs/backup", "internal", writable = true, available = true)
        )
        assertEquals(SyncEndpointValidation.Invalid("OVERLAPPING_ENDPOINTS"), result)
    }

    @Test
    fun unavailableSourceCannotBeInterpretedAsEmpty() {
        val result = SyncEndpointValidator.validate(
            SyncEndpoint("rclone://drive/source", "drive", writable = true, available = false),
            SyncEndpoint("file:///backup", "internal", writable = true, available = true)
        )
        assertEquals(SyncEndpointValidation.Invalid("SOURCE_UNAVAILABLE"), result)
    }

    @Test
    fun providerIdentityPreservesTheConfiguredRemote() {
        assertEquals(
            "rclone://box-account-id",
            DefaultSyncProviderCapabilityResolver.storageIdentity(
                "rclone://box-account-id/Travel"
            )
        )
        assertEquals(
            1L,
            DefaultSyncProviderCapabilityResolver.capabilities(
                "file:/storage/emulated/0/Documents"
            ).timestampPrecisionMillis
        )
    }

    @Test
    fun allowsIndependentStorageRoots() {
        val result = SyncEndpointValidator.validate(
            SyncEndpoint("file:///source", "internal", writable = true, available = true),
            SyncEndpoint("rclone://drive/backup", "drive", writable = true, available = true)
        )
        assertTrue(result is SyncEndpointValidation.Valid)
    }
}
