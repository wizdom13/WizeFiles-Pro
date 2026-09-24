package com.wisso.wizefiles.vault

import java.util.Random
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultEncryptedPayloadFuzzTest {
    @Test
    fun `bounded malformed corpus never escapes with indexing failures`() {
        val random = Random(0x575A_4655L)
        repeat(10_000) {
            val bytes = ByteArray(random.nextInt(64)).also(random::nextBytes)
            val failure = runCatching { VaultCrypto.EncryptedPayload.decode(bytes) }.exceptionOrNull()

            if (failure != null) {
                assertTrue(
                    "Unexpected parser failure ${failure::class.java.name}",
                    failure is IllegalArgumentException
                )
            }
        }
    }
}
