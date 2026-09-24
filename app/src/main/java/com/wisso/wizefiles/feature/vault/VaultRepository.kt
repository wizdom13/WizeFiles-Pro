// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import org.json.JSONObject

internal fun interface VaultAtomicWriter {
    fun write(file: File, block: (OutputStream) -> Unit)
}

internal object AndroidVaultAtomicWriter : VaultAtomicWriter {
    override fun write(file: File, block: (OutputStream) -> Unit) {
        file.parentFile?.mkdirs()
        val atomicFile = AtomicFile(file)
        val output = atomicFile.startWrite()
        try {
            block(output)
            atomicFile.finishWrite(output)
        } catch (throwable: Throwable) {
            atomicFile.failWrite(output)
            throw throwable
        }
    }
}

class VaultRepository internal constructor(
    private val context: Context,
    private val atomicWriter: VaultAtomicWriter
) {
    constructor(context: Context) : this(context, AndroidVaultAtomicWriter)

    private val vaultRoot: File
        get() = File(context.filesDir, "vaults")

    fun listMetadatas(): List<VaultMetadata> =
        vaultRoot.listFiles().orEmpty().mapNotNull { runCatching { loadMetadata(it.name) }.getOrNull() }

    fun createVault(name: String, password: CharArray): VaultCreation {
        val vaultId = UUID.randomUUID().toString()
        val vaultDir = vaultDir(vaultId)
        vaultDir.mkdirs()
        objectsDir(vaultId).mkdirs()
        val salt = VaultCrypto.randomBytes(16)
        val params = VaultCrypto.defaultArgon2Params
        val kek = VaultCrypto.derivePasswordKey(password, salt, params)
        val vmk = VaultCrypto.randomBytes(32)
        val wrapped = VaultCrypto.encryptAesGcm(vmk, kek)
        val metadata = VaultMetadata(
            version = 1,
            vaultId = vaultId,
            name = name,
            createdAt = System.currentTimeMillis(),
            saltBase64 = VaultCrypto.toBase64(salt),
            argon2Params = params,
            wrappedVmkBase64 = VaultCrypto.toBase64(wrapped.ciphertext),
            vmkWrapIvBase64 = VaultCrypto.toBase64(wrapped.iv),
            biometricEnabled = false,
            biometricWrappedVmkBase64 = null,
            biometricIvBase64 = null,
            biometricKeyAlias = "wizefiles.vault.$vaultId"
        )
        try {
            writeEncryptedIndex(vaultId, vmk, emptyList())
            writeMetadata(metadata)
            return VaultCreation(metadata, vmk.copyOf())
        } finally {
            VaultCrypto.zero(vmk)
            VaultCrypto.zero(kek)
            VaultCrypto.zero(salt)
        }
    }

    fun loadMetadata(vaultId: String): VaultMetadata {
        val metadataFile = File(vaultDir(vaultId), "metadata.json")
        val json = JSONObject(readAtomicBytes(metadataFile).toString(Charsets.UTF_8))
        return VaultMetadata.fromJson(json)
    }

    fun writeMetadata(metadata: VaultMetadata) {
        val metadataFile = File(vaultDir(metadata.vaultId), "metadata.json")
        atomicWriter.write(metadataFile) { output ->
            output.write(metadata.toJson().toString().toByteArray(Charsets.UTF_8))
        }
    }

    fun unwrapVmkWithPassword(metadata: VaultMetadata, password: CharArray): ByteArray {
        val salt = VaultCrypto.fromBase64(metadata.saltBase64)
        val kek = VaultCrypto.derivePasswordKey(password, salt, metadata.argon2Params)
        val payload = VaultCrypto.EncryptedPayload(
            iv = VaultCrypto.fromBase64(metadata.vmkWrapIvBase64),
            ciphertext = VaultCrypto.fromBase64(metadata.wrappedVmkBase64)
        )
        return try {
            VaultCrypto.decryptAesGcm(payload, kek)
        } finally {
            VaultCrypto.zero(kek)
            VaultCrypto.zero(salt)
        }
    }

    fun objectsDir(vaultId: String): File = File(vaultDir(vaultId), "objects")

    fun vaultDir(vaultId: String): File = File(vaultRoot, vaultId)

    fun readEncryptedIndex(vaultId: String): ByteArray =
        readAtomicBytes(File(vaultDir(vaultId), "tree/index.bin"))

    fun writeEncryptedIndex(vaultId: String, vmk: ByteArray, entries: List<VaultEntry>) {
        val indexFile = File(vaultDir(vaultId), "tree/index.bin")
        val plaintext = entries.toJsonArray().toString().toByteArray(Charsets.UTF_8)
        val encoded = try {
            VaultCrypto.encryptAesGcm(plaintext, vmk).encode()
        } finally {
            VaultCrypto.zero(plaintext)
        }
        try {
            atomicWriter.write(indexFile) { output -> output.write(encoded) }
        } finally {
            VaultCrypto.zero(encoded)
        }
    }

    fun readIndex(vaultId: String, vmk: ByteArray): MutableList<VaultEntry> {
        val treeFile = File(vaultDir(vaultId), "tree/index.bin")
        val encrypted = try {
            readAtomicBytes(treeFile)
        } catch (_: FileNotFoundException) {
            return mutableListOf()
        }
        return try {
            val payload = VaultCrypto.EncryptedPayload.decode(encrypted)
            val plaintext = VaultCrypto.decryptAesGcm(payload, vmk)
            try {
                org.json.JSONArray(String(plaintext, Charsets.UTF_8)).toVaultEntries().toMutableList()
            } finally {
                VaultCrypto.zero(plaintext)
            }
        } finally {
            VaultCrypto.zero(encrypted)
        }
    }

    fun writeEncryptedObject(vaultId: String, objectId: String, vmk: ByteArray, input: InputStream): Long {
        val objectFile = File(objectsDir(vaultId), objectId)
        val (cipher, iv) = VaultCrypto.createEncryptCipher(vmk)
        var written = 0L
        atomicWriter.write(objectFile) { output ->
            output.write(iv.size)
            output.write(iv)
            CipherOutputStream(NonClosingOutputStream(output), cipher).use { encryptedOutput ->
                written = copyToCounting(input, encryptedOutput)
            }
        }
        return written
    }

    fun writeObject(vaultId: String, objectId: String, encrypted: ByteArray) {
        atomicWriter.write(File(objectsDir(vaultId), objectId)) { output -> output.write(encrypted) }
    }

    fun readObject(vaultId: String, objectId: String): ByteArray =
        readAtomicBytes(File(objectsDir(vaultId), objectId))

    fun copyDecryptedObjectTo(
        vaultId: String,
        objectId: String,
        vmk: ByteArray,
        output: OutputStream
    ): Long = openDecryptedObject(vaultId, objectId, vmk).use { input ->
        copyToCounting(input, output)
    }

    fun deleteObject(vaultId: String, objectId: String) {
        AtomicFile(File(objectsDir(vaultId), objectId)).delete()
    }

    fun reconcileObjects(vaultId: String, referencedObjectIds: Set<String>): Int {
        val directory = objectsDir(vaultId).apply { mkdirs() }

        referencedObjectIds.forEach { objectId ->
            val objectFile = File(directory, objectId)
            runCatching { AtomicFile(objectFile).openRead().use { } }
            File(directory, "$objectId.new").delete()
        }

        var deleted = 0
        directory.listFiles().orEmpty().forEach { file ->
            val objectId = file.name.removeSuffix(".new").removeSuffix(".bak")
            if (objectId !in referencedObjectIds && file.delete()) {
                deleted += 1
            }
        }
        return deleted
    }

    fun deleteVault(vaultId: String) {
        vaultDir(vaultId).deleteRecursively()
    }

    private fun readAtomicBytes(file: File): ByteArray = AtomicFile(file).openRead().use { it.readBytes() }

    private fun openDecryptedObject(vaultId: String, objectId: String, vmk: ByteArray): InputStream {
        val encryptedInput = AtomicFile(File(objectsDir(vaultId), objectId)).openRead()
        try {
            val ivSize = encryptedInput.read()
            if (ivSize !in 1..MAX_IV_SIZE_BYTES) {
                throw IOException("Invalid encrypted Vault object IV length")
            }
            val iv = ByteArray(ivSize)
            encryptedInput.readFully(iv)
            val cipher = VaultCrypto.createDecryptCipher(iv, vmk)
            return CipherInputStream(encryptedInput, cipher)
        } catch (throwable: Throwable) {
            encryptedInput.close()
            throw throwable
        }
    }

    private companion object {
        const val MAX_IV_SIZE_BYTES = 32
    }
}

data class VaultCreation(
    val metadata: VaultMetadata,
    val vmk: ByteArray
)

private class NonClosingOutputStream(output: OutputStream) : FilterOutputStream(output) {
    override fun close() {
        flush()
    }
}

private fun copyToCounting(input: InputStream, output: OutputStream): Long {
    var total = 0L
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    try {
        while (true) {
            val read = input.read(buffer)
            if (read == -1) {
                break
            }
            output.write(buffer, 0, read)
            total += read.toLong()
        }
        return total
    } finally {
        VaultCrypto.zero(buffer)
    }
}

private fun InputStream.readFully(destination: ByteArray) {
    var offset = 0
    while (offset < destination.size) {
        val read = read(destination, offset, destination.size - offset)
        if (read == -1) {
            throw IOException("Unexpected end of encrypted Vault object")
        }
        offset += read
    }
}
