package com.wisso.wizefiles.vault

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultOpenSessionSourceTest {

    @Test
    fun vaultOpenSessionsDoNotWritePlaintextCacheFiles() {
        val manager = sourceFile("src/main/java/com/wisso/wizefiles/feature/vault/VaultOpenSessionManager.kt")
        val bridge = sourceFile("src/main/java/com/wisso/wizefiles/feature/vault/VaultOpenSessionFileBridge.kt")
        val store = sourceFile("src/main/java/com/wisso/wizefiles/feature/vault/VaultOpenSessionEncryptedStore.kt")

        assertFalse(manager.contains("cacheDir"))
        assertFalse(bridge.contains("initialBytes"))
        assertFalse(bridge.contains("ensureCapacity"))
        assertFalse(bridge.contains("buffer.copyOf"))
        assertFalse(bridge.contains("readDecryptedBytes"))
        assertTrue(store.contains("VaultCrypto.encryptAesGcm"))
        assertTrue(store.contains("CHUNK_SIZE_BYTES = 64 * 1024"))
    }

    @Test
    fun vaultOpenSessionsUseStreamingContentBackedBridge() {
        val manager = sourceFile("src/main/java/com/wisso/wizefiles/feature/vault/VaultOpenSessionManager.kt")
        val bridge = sourceFile("src/main/java/com/wisso/wizefiles/feature/vault/VaultOpenSessionFileBridge.kt")
        val provider = sourceFile("src/main/java/com/wisso/wizefiles/core/files/provider/FileProvider.kt")
        val vaultManager = sourceFile("src/main/java/com/wisso/wizefiles/feature/vault/VaultManager.kt")
        val repository = sourceFile("src/main/java/com/wisso/wizefiles/feature/vault/VaultRepository.kt")

        assertTrue(manager.contains("VaultOpenSessionUri.create(session)"))
        assertTrue(bridge.contains("vault-session"))
        assertTrue(bridge.contains("VaultOpenSessionEncryptedStore"))
        assertTrue(bridge.contains("replaceFile(session.vaultId, session.entryId, input)"))
        assertTrue(provider.contains("VaultOpenSessionFileBridge.openFile"))
        assertTrue(vaultManager.contains("input: InputStream"))
        assertTrue(repository.contains("CipherInputStream"))
        assertTrue(repository.contains("copyDecryptedObjectTo"))
    }

    private fun sourceFile(path: String): String {
        val direct = File(path)
        if (direct.exists()) return direct.readText()
        val fromRepoRoot = File("app", path)
        if (fromRepoRoot.exists()) return fromRepoRoot.readText()
        throw java.io.FileNotFoundException(path)
    }
}
