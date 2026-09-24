// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.share

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeNoException
import org.junit.Test

class ShareGatewayTest {
    @Test
    fun `selected root exposes no parent path`() {
        val directory=Files.createTempDirectory("wize-share")
        try {
            val gateway=gateway(directory)
            assertEquals(
                directory.resolve("child.txt"),
                gateway.resolve("r","child.txt",ShareCapability.CREATE).path
            )
            assertThrows(IllegalArgumentException::class.java) {
                gateway.resolve("r","../outside",ShareCapability.READ)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `exchange may create but may not delete`() {
        val directory=Files.createTempDirectory("wize-share")
        try {
            val root=ShareRoot(
                id="r",
                profileId="p",
                alias="Selected",
                appPathUri=directory.toUri().toString(),
                providerIdentity="local"
            )
            val gateway=ShareGateway(ShareProfile(id="p",name="PC"),listOf(root)) { directory }
            gateway.resolve("r","new",ShareCapability.CREATE)
            assertThrows(IllegalArgumentException::class.java) {
                gateway.resolve("r","old",ShareCapability.DELETE)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `intermediate symbolic link cannot escape selected root`() {
        val directory=Files.createTempDirectory("wize-share")
        val outside=Files.createTempDirectory("wize-share-outside")
        try {
            Files.write(outside.resolve("secret.txt"),"secret".toByteArray())
            try {
                Files.createSymbolicLink(directory.resolve("escape"),outside)
            } catch (exception:Exception) {
                assumeNoException(exception)
            }
            val gateway=gateway(directory)
            assertThrows(IllegalArgumentException::class.java) {
                gateway.resolve("r","escape/secret.txt",ShareCapability.READ)
            }
            assertThrows(IllegalArgumentException::class.java) {
                gateway.resolve("r","escape/new.txt",ShareCapability.CREATE)
            }
        } finally {
            directory.toFile().deleteRecursively()
            outside.toFile().deleteRecursively()
        }
    }

    private fun gateway(directory:Path):ShareGateway {
        val profile=ShareProfile(
            id="p",
            name="PC",
            permission=SharePermission.FULL_MANAGEMENT
        )
        val root=ShareRoot(
            id="r",
            profileId="p",
            alias="Selected",
            appPathUri=directory.toUri().toString(),
            providerIdentity="local"
        )
        return ShareGateway(profile,listOf(root)) { directory }
    }
}
