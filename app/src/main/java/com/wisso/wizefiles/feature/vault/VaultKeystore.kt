// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator

object VaultKeystore {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun getOrCreateKey(alias: String) {
        val keystore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keystore.containsAlias(alias)) return
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generator.init(builder.setIsStrongBoxBacked(true).build())
                generator.generateKey()
                return
            } catch (_: StrongBoxUnavailableException) {
            } catch (_: Exception) {
            }
        }
        generator.init(builder.build())
        generator.generateKey()
    }

    fun encryptCipher(alias: String): Cipher {
        getOrCreateKey(alias)
        val keystore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val key = keystore.getKey(alias, null)
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    fun decryptCipher(alias: String, iv: ByteArray): Cipher {
        val keystore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val key = keystore.getKey(alias, null)
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
        }
    }

    fun deleteKey(alias: String) {
        val keystore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keystore.containsAlias(alias)) {
            keystore.deleteEntry(alias)
        }
    }
}
