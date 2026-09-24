package com.wisso.wizefiles.feature.filejobs

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.mindrot.jbcrypt.BCrypt
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

enum class FileEncryptionAlgorithm(val id: Int) {
    AES_256_GCM(1),
    CHACHA20_POLY1305(2);

    companion object {
        fun fromId(id: Int): FileEncryptionAlgorithm = entries.firstOrNull { it.id == id }
            ?: throw FileCryptoException("Unsupported encryption algorithm")
    }
}

enum class FileKdfAlgorithm(val id: Int) {
    ARGON2ID(1),
    BCRYPT(2);

    companion object {
        fun fromId(id: Int): FileKdfAlgorithm = entries.firstOrNull { it.id == id }
            ?: throw FileCryptoException("Unsupported key derivation algorithm")
    }
}

data class FileCryptoHeader(
    val algorithm: FileEncryptionAlgorithm,
    val kdf: FileKdfAlgorithm,
    val salt: ByteArray,
    val nonce: ByteArray,
    val originalName: String
)

class FileCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

object FileCrypto {
    private val MAGIC = "WZFENC1".toByteArray(StandardCharsets.US_ASCII)
    private const val CURRENT_VERSION = 3
    private const val LEGACY_VERSION = 2
    private const val SALT_LENGTH_BYTES = 16
    private const val NONCE_LENGTH_BYTES = 12
    private const val MAX_ORIGINAL_NAME_UTF8_BYTES = 255
    private const val ARGON_MEMORY_KIB = 64 * 1024
    private const val ARGON_ITERATIONS = 2
    private const val ARGON_PARALLELISM = 1
    private const val BCRYPT_PBKDF_ROUNDS = 64
    private const val KEY_LEN = 32

    fun encrypt(
        input: InputStream,
        output: OutputStream,
        password: CharArray,
        algorithm: FileEncryptionAlgorithm,
        kdf: FileKdfAlgorithm,
        originalName: String
    ) {
        val safeOriginalName = requireSafeOriginalName(originalName)
        val salt = randomBytes(SALT_LENGTH_BYTES)
        val nonce = randomBytes(NONCE_LENGTH_BYTES)
        val key = deriveKey(password, salt, kdf)
        try {
            val header = FileCryptoHeader(algorithm, kdf, salt, nonce, safeOriginalName)
            val encodedHeader = encodeHeader(header, CURRENT_VERSION)
            output.write(encodedHeader)
            createEncryptingStream(output, algorithm, key, nonce, encodedHeader).use { cipherOutput ->
                input.copyTo(cipherOutput)
            }
        } finally {
            zero(key)
            zero(salt)
            zero(nonce)
        }
    }

    fun decrypt(input: InputStream, output: OutputStream, password: CharArray): FileCryptoHeader {
        val parsedHeader = readHeaderFrame(input)
        val header = parsedHeader.header
        val key = deriveKey(password, header.salt, header.kdf)
        try {
            try {
                createDecryptingStream(
                    input,
                    header.algorithm,
                    key,
                    header.nonce,
                    parsedHeader.encoded.takeIf { parsedHeader.version >= CURRENT_VERSION }
                ).use { cipherInput ->
                    cipherInput.copyTo(output)
                }
            } catch (e: AEADBadTagException) {
                throw FileCryptoException("Wrong password or corrupted encrypted file", e)
            } catch (e: java.io.IOException) {
                if (e.cause is AEADBadTagException) {
                    throw FileCryptoException("Wrong password or corrupted encrypted file", e)
                }
                throw e
            }
            return header
        } finally {
            zero(key)
        }
    }

    private fun createEncryptingStream(
        output: OutputStream,
        algorithm: FileEncryptionAlgorithm,
        key: ByteArray,
        nonce: ByteArray,
        authenticatedHeader: ByteArray
    ): OutputStream {
        val cipher = createCipher(
            Cipher.ENCRYPT_MODE,
            algorithm,
            key,
            nonce,
            authenticatedHeader
        )
        return CipherOutputStream(output, cipher)
    }

    private fun createDecryptingStream(
        input: InputStream,
        algorithm: FileEncryptionAlgorithm,
        key: ByteArray,
        nonce: ByteArray,
        authenticatedHeader: ByteArray?
    ): InputStream {
        val cipher = createCipher(
            Cipher.DECRYPT_MODE,
            algorithm,
            key,
            nonce,
            authenticatedHeader
        )
        return CipherInputStream(input, cipher)
    }

    private fun createCipher(
        mode: Int,
        algorithm: FileEncryptionAlgorithm,
        key: ByteArray,
        nonce: ByteArray,
        authenticatedHeader: ByteArray?
    ): Cipher {
        return try {
            val cipher = when (algorithm) {
                FileEncryptionAlgorithm.AES_256_GCM -> Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                }
                FileEncryptionAlgorithm.CHACHA20_POLY1305 -> Cipher.getInstance("ChaCha20-Poly1305").apply {
                    init(mode, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce))
                }
            }
            authenticatedHeader?.let(cipher::updateAAD)
            cipher
        } catch (e: GeneralSecurityException) {
            throw FileCryptoException("Unable to initialize cipher", e)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, kdf: FileKdfAlgorithm): ByteArray =
        when (kdf) {
            FileKdfAlgorithm.ARGON2ID -> deriveArgon2id(password, salt)
            FileKdfAlgorithm.BCRYPT -> deriveBcrypt(password, salt)
        }

    private fun deriveArgon2id(password: CharArray, salt: ByteArray): ByteArray {
        val passwordBytes = password.concatToString().toByteArray(StandardCharsets.UTF_8)
        return try {
            val generator = Argon2BytesGenerator()
            generator.init(
                Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withSalt(salt)
                    .withMemoryAsKB(ARGON_MEMORY_KIB)
                    .withIterations(ARGON_ITERATIONS)
                    .withParallelism(ARGON_PARALLELISM)
                    .build()
            )
            ByteArray(KEY_LEN).also { generator.generateBytes(passwordBytes, it, 0, it.size) }
        } finally {
            password.fill('\u0000')
            zero(passwordBytes)
        }
    }

    private fun deriveBcrypt(password: CharArray, salt: ByteArray): ByteArray {
        val passwordBytes = password.concatToString().toByteArray(StandardCharsets.UTF_8)
        return try {
            ByteArray(KEY_LEN).also {
                BCrypt().pbkdf(passwordBytes, salt, BCRYPT_PBKDF_ROUNDS, it)
            }
        } finally {
            password.fill('\u0000')
            zero(passwordBytes)
        }
    }

    private fun encodeHeader(header: FileCryptoHeader, version: Int): ByteArray {
        val bytes = ByteArrayOutputStream()
        val data = DataOutputStream(bytes)
        val nameBytes = requireSafeOriginalName(header.originalName)
            .toByteArray(StandardCharsets.UTF_8)
        data.write(MAGIC)
        data.writeByte(version)
        data.writeByte(header.algorithm.id)
        data.writeByte(header.kdf.id)
        data.writeByte(header.salt.size)
        data.write(header.salt)
        data.writeByte(header.nonce.size)
        data.write(header.nonce)
        data.writeShort(nameBytes.size)
        data.write(nameBytes)
        data.writeShort(0)
        data.flush()
        return bytes.toByteArray()
    }

    fun readHeader(input: InputStream): FileCryptoHeader = readHeaderFrame(input).header

    private fun readHeaderFrame(input: InputStream): ParsedHeader {
        val data = DataInputStream(input)
        val magic = ByteArray(MAGIC.size)
        try {
            data.readFully(magic)
        } catch (e: EOFException) {
            throw FileCryptoException("Not a supported encrypted file", e)
        }
        if (!magic.contentEquals(MAGIC)) {
            throw FileCryptoException("Not a supported encrypted file")
        }
        val version = data.readUnsignedByte()
        if (version !in setOf(LEGACY_VERSION, CURRENT_VERSION)) {
            throw FileCryptoException("Unsupported encrypted file version")
        }
        val algorithm = FileEncryptionAlgorithm.fromId(data.readUnsignedByte())
        val kdf = FileKdfAlgorithm.fromId(data.readUnsignedByte())
        val salt = ByteArray(data.readUnsignedByte()).also { data.readFully(it) }
        val nonce = ByteArray(data.readUnsignedByte()).also { data.readFully(it) }
        if (salt.size != SALT_LENGTH_BYTES || nonce.size != NONCE_LENGTH_BYTES) {
            throw FileCryptoException("Encrypted file has invalid cryptographic parameters")
        }
        val nameSize = data.readUnsignedShort()
        if (nameSize == 0 || nameSize > MAX_ORIGINAL_NAME_UTF8_BYTES) {
            throw FileCryptoException("Encrypted file has an invalid original name")
        }
        val name = ByteArray(nameSize).also { data.readFully(it) }
            .toString(StandardCharsets.UTF_8)
        requireSafeOriginalName(name)
        val reservedSize = data.readUnsignedShort()
        if (reservedSize > 0) {
            val reserved = ByteArray(reservedSize)
            data.readFully(reserved)
            if (version >= CURRENT_VERSION || reserved.any { it.toInt() != 0 }) {
                throw FileCryptoException("Encrypted file has unsupported header data")
            }
        }
        val header = FileCryptoHeader(algorithm, kdf, salt, nonce, name)
        return ParsedHeader(version, header, encodeHeader(header, version))
    }

    internal fun requireSafeOriginalName(value: String): String {
        val encodedLength = value.toByteArray(StandardCharsets.UTF_8).size
        if (
            value.isBlank() ||
            value == "." ||
            value == ".." ||
            encodedLength !in 1..MAX_ORIGINAL_NAME_UTF8_BYTES ||
            value.any { it == '/' || it == '\\' || it == '\u0000' || it == '\uFFFD' || it.isISOControl() } ||
            WINDOWS_ABSOLUTE_PATH.matches(value)
        ) {
            throw FileCryptoException("Encrypted file has an unsafe original name")
        }
        return value
    }

    private data class ParsedHeader(
        val version: Int,
        val header: FileCryptoHeader,
        val encoded: ByteArray
    )

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

    fun zero(bytes: ByteArray) {
        bytes.fill(0)
    }

    private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:.*")
}
