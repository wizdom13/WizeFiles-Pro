package com.wisso.wizefiles.settings

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class SettingsBackupSerializerTest {

    private val serializer = SettingsBackupSerializer()

    @Test
    fun `backup serialization writes expected keys and values when not encrypted`() {
        val json = serializer.serialize(
            data = SettingsBackupData(
                formatVersion = SettingsBackupSerializer.FORMAT_VERSION,
                settings = mapOf(
                    "key_night_mode" to BackupValue("string", "1"),
                    "key_recycle_bin" to BackupValue("boolean", true)
                ),
                secretSettings = mapOf("key_dummy_secret" to "secret")
            ),
            encrypt = false
        )

        assertTrue(json.contains("\"formatVersion\":2"))
        assertTrue(json.contains("\"encrypted\":false"))
        assertTrue(json.contains("\"key_night_mode\""))
        assertTrue(json.contains("\"key_recycle_bin\""))
        assertTrue(json.contains("\"key_dummy_secret\""))
    }

    @Test
    fun `encrypted backup hides plaintext secret and round trips with password`() {
        val json = serializer.serialize(
            data = SettingsBackupData(
                formatVersion = SettingsBackupSerializer.FORMAT_VERSION,
                settings = mapOf("key_night_mode" to BackupValue("string", "2")),
                secretSettings = mapOf("key_dummy_secret" to "abc")
            ),
            password = "backup-pass-123".toCharArray(),
            encrypt = true
        )

        assertTrue(json.contains("\"encrypted\":true"))
        assertFalse(json.contains("\"key_dummy_secret\":\"abc\""))
        assertTrue(serializer.inspect(json).isEncrypted)

        val data = serializer.deserialize(json, password = "backup-pass-123".toCharArray())
        assertEquals("2", data.settings["key_night_mode"]?.value)
        assertEquals("abc", data.secretSettings["key_dummy_secret"])
    }

    @Test
    fun `legacy restore deserialization applies values correctly`() {
        val data = serializer.deserialize(
            """
            {
              "formatVersion":1,
              "settings":{
                "key_night_mode":{"type":"string","value":"2"},
                "key_recycle_bin":{"type":"boolean","value":false}
              },
              "secretSettings":{"key_dummy_secret":"abc"}
            }
            """.trimIndent()
        )

        assertEquals("2", data.settings["key_night_mode"]?.value)
        assertEquals(false, data.settings["key_recycle_bin"]?.value)
        assertEquals("abc", data.secretSettings["key_dummy_secret"])
        assertFalse(serializer.inspect("""{"formatVersion":1,"settings":{},"secretSettings":{}}""").isEncrypted)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid backup version is rejected`() {
        serializer.deserialize("""{"formatVersion":99,"settings":{}}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encrypted backup requires password`() {
        val encrypted = serializer.serialize(
            data = SettingsBackupData(
                formatVersion = SettingsBackupSerializer.FORMAT_VERSION,
                settings = emptyMap(),
                secretSettings = mapOf("key_dummy_secret" to "secret")
            ),
            password = "abc123".toCharArray(),
            encrypt = true
        )

        serializer.deserialize(encrypted)
    }

    @Test
    fun `encrypted backup rejects unsupported algorithms before decryption`() {
        val encrypted = encryptedBackupJson()
        encrypted.getJSONObject("encryption").put("algorithm", "AES/CBC/PKCS5Padding")

        assertTrue(
            runCatching {
                serializer.deserialize(encrypted.toString(), "backup-pass-123".toCharArray())
            }.isFailure
        )
    }

    @Test
    fun `encrypted backup rejects excessive KDF work before derivation`() {
        val encrypted = encryptedBackupJson()
        encrypted.getJSONObject("encryption").put("iterations", Int.MAX_VALUE)

        assertTrue(
            runCatching {
                serializer.deserialize(encrypted.toString(), "backup-pass-123".toCharArray())
            }.isFailure
        )
    }

    @Test
    fun `encrypted backup rejects malformed salt and IV lengths`() {
        val encrypted = encryptedBackupJson()
        encrypted.getJSONObject("encryption").put("salt", "AA==")

        assertTrue(
            runCatching {
                serializer.deserialize(encrypted.toString(), "backup-pass-123".toCharArray())
            }.isFailure
        )
    }

    @Test
    fun `default backup file name ends with wzf`() {
        val fileName = SettingsBackupFileName.default(LocalDate.of(2026, 4, 8))

        assertTrue(fileName.endsWith(".wzf"))
        assertEquals("WizeFiles_backup08042026.wzf", fileName)
    }

    private fun encryptedBackupJson(): JSONObject = JSONObject(
        serializer.serialize(
            data = SettingsBackupData(
                formatVersion = SettingsBackupSerializer.FORMAT_VERSION,
                settings = mapOf("key_night_mode" to BackupValue("string", "2")),
                secretSettings = mapOf("key_dummy_secret" to "abc")
            ),
            password = "backup-pass-123".toCharArray(),
            encrypt = true
        )
    )
}
