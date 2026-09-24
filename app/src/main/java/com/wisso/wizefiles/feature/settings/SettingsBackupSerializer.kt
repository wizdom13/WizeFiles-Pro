package com.wisso.wizefiles.settings

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Parcel
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.appClassLoader
import com.wisso.wizefiles.core.app.defaultSharedPreferences
import com.wisso.wizefiles.core.app.secretStore
import com.wisso.wizefiles.feature.filebrowser.FileSortOptions
import com.wisso.wizefiles.feature.filebrowser.GridColumnOverrides
import com.wisso.wizefiles.navigation.BookmarkDirectory
import com.wisso.wizefiles.navigation.StandardDirectorySettings
import com.wisso.wizefiles.searchindex.SearchIndexManager
import com.wisso.wizefiles.security.AppSecurityManager
import com.wisso.wizefiles.security.SecretStore
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLocalFileOrNull
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.ui.FileIconShape
import com.wisso.wizefiles.util.asBase64
import com.wisso.wizefiles.util.toBase64
import com.wisso.wizefiles.util.toByteArray
import com.wisso.wizefiles.util.use
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONException
import org.json.JSONObject

class SettingsBackupSerializer(
    private val secureRandom: SecureRandom = SecureRandom()
) {
    fun serialize(
        data: SettingsBackupData,
        password: CharArray? = null,
        encrypt: Boolean = password != null
    ): String {
        val plainRoot = serializePlainPayload(data)
        if (!encrypt) {
            return plainRoot.toString()
        }
        val normalizedPassword = password?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Password required for encrypted backup")
        val encryptedPayload = encrypt(plainRoot.toString(), normalizedPassword)
        return JSONObject()
            .put(FIELD_FORMAT_VERSION, FORMAT_VERSION)
            .put(FIELD_ENCRYPTED, true)
            .put(
                FIELD_ENCRYPTION,
                JSONObject()
                    .put(FIELD_ALGORITHM, AES_TRANSFORMATION)
                    .put(FIELD_KDF, PBKDF2_ALGORITHM)
                    .put(FIELD_ITERATIONS, PBKDF2_ITERATIONS)
                    .put(FIELD_SALT, encryptedPayload.salt.toBase64().value)
                    .put(FIELD_IV, encryptedPayload.iv.toBase64().value)
            )
            .put(FIELD_CIPHERTEXT, encryptedPayload.ciphertext.toBase64().value)
            .toString()
    }

    fun inspect(content: String): SettingsBackupEncryptionInfo {
        try {
            val root = JSONObject(content)
            return when (root.optInt(FIELD_FORMAT_VERSION, -1)) {
                LEGACY_FORMAT_VERSION -> SettingsBackupEncryptionInfo(isEncrypted = false)
                FORMAT_VERSION -> SettingsBackupEncryptionInfo(root.optBoolean(FIELD_ENCRYPTED, false))
                else -> throw IllegalArgumentException("Unsupported backup version")
            }
        } catch (e: JSONException) {
            throw IllegalArgumentException("Invalid backup file", e)
        }
    }

    fun deserialize(content: String, password: CharArray? = null): SettingsBackupData {
        try {
            val root = JSONObject(content)
            return deserializeRoot(root, password)
        } catch (e: JSONException) {
            throw IllegalArgumentException("Invalid backup file", e)
        }
    }

    private fun deserializeRoot(root: JSONObject, password: CharArray?): SettingsBackupData = when (
        root.opt(FIELD_FORMAT_VERSION) as? Number
    ) {
        null -> throw IllegalArgumentException("Missing backup version")
        else -> (root.opt(FIELD_FORMAT_VERSION) as Number).toInt()
    }.let { version -> when (version) {
        LEGACY_FORMAT_VERSION -> deserializePlainPayload(root)
        FORMAT_VERSION -> {
            validateRootSchemaV2(root)
            if (root.optBoolean(FIELD_ENCRYPTED, false)) {
                val normalizedPassword = password?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalArgumentException("Password required for encrypted backup")
                val encryptionJson = root.optJSONObject(FIELD_ENCRYPTION)
                    ?: throw IllegalArgumentException("Missing encryption metadata")
                require(encryptionJson.optString(FIELD_ALGORITHM) == AES_TRANSFORMATION) {
                    "Unsupported backup encryption algorithm"
                }
                require(encryptionJson.optString(FIELD_KDF) == PBKDF2_ALGORITHM) {
                    "Unsupported backup key derivation function"
                }
                val iterationValue = encryptionJson.opt(FIELD_ITERATIONS) as? Number
                    ?: throw IllegalArgumentException("Missing backup KDF iteration count")
                val iterationLong = iterationValue.toLong()
                require(iterationValue.toDouble() == iterationLong.toDouble() &&
                    iterationLong in MIN_PBKDF2_ITERATIONS..MAX_PBKDF2_ITERATIONS) {
                    "Unsupported backup KDF iteration count"
                }
                val salt = decodeFixedBase64(
                    encryptionJson.optString(FIELD_SALT),
                    SALT_LENGTH_BYTES,
                    "salt"
                )
                val iv = decodeFixedBase64(
                    encryptionJson.optString(FIELD_IV),
                    IV_LENGTH_BYTES,
                    "IV"
                )
                val ciphertext = decodeBoundedBase64(
                    root.optString(FIELD_CIPHERTEXT),
                    MAX_CIPHERTEXT_BYTES,
                    "ciphertext"
                )
                require(ciphertext.size >= GCM_TAG_LENGTH_BITS / 8) {
                    "Backup ciphertext is too short"
                }
                val iterations = iterationLong.toInt()
                val plaintext = decrypt(
                    EncryptedPayload(
                        salt = salt,
                        iv = iv,
                        ciphertext = ciphertext,
                        iterations = iterations
                    ),
                    normalizedPassword
                )
                deserialize(plaintext)
            } else {
                deserializePlainPayload(root)
            }
        }
        else -> throw IllegalArgumentException("Unsupported backup version")
    } }

    private fun serializePlainPayload(data: SettingsBackupData): JSONObject {
        val root = JSONObject()
        root.put(FIELD_FORMAT_VERSION, FORMAT_VERSION)
        root.put(FIELD_ENCRYPTED, false)
        val settingsObject = JSONObject()
        data.settings.forEach { (key, backupValue) ->
            settingsObject.put(
                key,
                JSONObject().put(FIELD_TYPE, backupValue.type).put(FIELD_VALUE, backupValue.value)
            )
        }
        root.put(FIELD_SETTINGS, settingsObject)
        val secretObject = JSONObject()
        data.secretSettings.forEach { (key, value) -> secretObject.put(key, value) }
        root.put(FIELD_SECRET_SETTINGS, secretObject)
        return root
    }

    private fun deserializePlainPayload(root: JSONObject): SettingsBackupData {
        val formatVersion = root.optInt(FIELD_FORMAT_VERSION, -1)
        if (formatVersion != LEGACY_FORMAT_VERSION && formatVersion != FORMAT_VERSION) {
            throw IllegalArgumentException("Unsupported backup version")
        }
        val settings = mutableMapOf<String, BackupValue>()
        val settingsJson = root.optJSONObject(FIELD_SETTINGS)
            ?: throw IllegalArgumentException("Missing settings payload")
        settingsJson.keys().forEach { key ->
            val valueObject = settingsJson.optJSONObject(key)
                ?: return@forEach
            val type = valueObject.optString(FIELD_TYPE)
            val value = valueObject.opt(FIELD_VALUE)
            settings[key] = BackupValue(type = type, value = if (value == JSONObject.NULL) null else value)
        }

        val secretSettings = mutableMapOf<String, String?>()
        val secretJson = root.optJSONObject(FIELD_SECRET_SETTINGS)
        if (secretJson != null) {
            secretJson.keys().forEach { key ->
                val secretValue = secretJson.opt(key)
                secretSettings[key] = if (secretValue == JSONObject.NULL) null else secretValue as? String
            }
        }
        return SettingsBackupData(formatVersion, settings, secretSettings)
    }

    private fun encrypt(content: String, password: CharArray): EncryptedPayload {
        val salt = randomBytes(SALT_LENGTH_BYTES)
        val iv = randomBytes(IV_LENGTH_BYTES)
        val key = deriveKey(password, salt, PBKDF2_ITERATIONS)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
        val ciphertext = cipher.doFinal(content.toByteArray(StandardCharsets.UTF_8))
        return EncryptedPayload(salt = salt, iv = iv, ciphertext = ciphertext, iterations = PBKDF2_ITERATIONS)
    }

    private fun decrypt(payload: EncryptedPayload, password: CharArray): String {
        val key = deriveKey(password, payload.salt, payload.iterations)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.iv))
        }
        return try {
            val plaintext = cipher.doFinal(payload.ciphertext)
            String(plaintext, StandardCharsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw IllegalArgumentException("Incorrect password or corrupted backup file", e)
        } catch (e: GeneralSecurityException) {
            throw IllegalArgumentException("Unable to decrypt backup file", e)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val keySpec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(keySpec).encoded
        } finally {
            keySpec.clearPassword()
        }
    }

    private fun validateRootSchemaV2(root: JSONObject) {
        val allowedFields = if (root.optBoolean(FIELD_ENCRYPTED, false)) {
            setOf(FIELD_FORMAT_VERSION, FIELD_ENCRYPTED, FIELD_ENCRYPTION, FIELD_CIPHERTEXT)
        } else {
            setOf(FIELD_FORMAT_VERSION, FIELD_ENCRYPTED, FIELD_SETTINGS, FIELD_SECRET_SETTINGS)
        }
        root.keys().forEach { key ->
            if (key !in allowedFields) {
                throw IllegalArgumentException("Unsupported backup schema")
            }
        }
    }

    private fun decodeFixedBase64(value: String, size: Int, label: String): ByteArray =
        decodeBoundedBase64(value, size, label).also {
            require(it.size == size) { "Backup $label has an invalid length" }
        }

    private fun decodeBoundedBase64(value: String, maxBytes: Int, label: String): ByteArray {
        val maximumEncodedLength = ((maxBytes + 2L) / 3L) * 4L
        require(value.length.toLong() <= maximumEncodedLength) {
            "Backup $label is too large"
        }
        val decoded = value.asBase64().toByteArray()
        require(decoded.size <= maxBytes) { "Backup $label is too large" }
        return decoded
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also { secureRandom.nextBytes(it) }

    private data class EncryptedPayload(
        val salt: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray,
        val iterations: Int
    )

    companion object {
        const val FORMAT_VERSION = 2
        private const val LEGACY_FORMAT_VERSION = 1
        private const val FIELD_FORMAT_VERSION = "formatVersion"
        private const val FIELD_ENCRYPTED = "encrypted"
        private const val FIELD_ENCRYPTION = "encryption"
        private const val FIELD_CIPHERTEXT = "ciphertext"
        private const val FIELD_SETTINGS = "settings"
        private const val FIELD_SECRET_SETTINGS = "secretSettings"
        private const val FIELD_TYPE = "type"
        private const val FIELD_VALUE = "value"
        private const val FIELD_ALGORITHM = "algorithm"
        private const val FIELD_KDF = "kdf"
        private const val FIELD_ITERATIONS = "iterations"
        private const val FIELD_SALT = "salt"
        private const val FIELD_IV = "iv"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 120_000
        private const val MIN_PBKDF2_ITERATIONS = 100_000L
        private const val MAX_PBKDF2_ITERATIONS = 1_000_000L
        private const val MAX_CIPHERTEXT_BYTES = 256 * 1024
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val SALT_LENGTH_BYTES = 16
        private const val IV_LENGTH_BYTES = 12
        private const val KEY_LENGTH_BITS = 256
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
