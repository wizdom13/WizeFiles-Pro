package com.wisso.wizefiles.vault

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultMetadataTest {
    @Test
    fun metadataJsonRoundtrip() {
        val metadata = VaultMetadata(
            version = 1,
            vaultId = "v1",
            name = "Vault",
            createdAt = 123L,
            saltBase64 = "salt",
            argon2Params = Argon2Params(65536, 2, 1, 32),
            wrappedVmkBase64 = "wrapped",
            vmkWrapIvBase64 = "iv",
            biometricEnabled = true,
            biometricWrappedVmkBase64 = "bio",
            biometricIvBase64 = "bioiv",
            biometricKeyAlias = "alias"
        )

        val parsed = VaultMetadata.fromJson(metadata.toJson())
        assertEquals(metadata, parsed)
    }
}
