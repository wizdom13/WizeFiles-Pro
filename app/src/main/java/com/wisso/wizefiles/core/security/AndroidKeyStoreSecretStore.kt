// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AndroidKeyStoreSecretStore private constructor(
    private val sharedPreferences: SharedPreferences
) : SecretStore {

    override fun getSecret(key: String): String? = sharedPreferences.getString(key, null)

    override fun putSecret(key: String, value: String?) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    override fun removeSecret(key: String) {
        sharedPreferences.edit().remove(key).apply()
    }

    companion object {
        private const val PREFERENCES_FILE = "secret_store"

        fun create(context: Context): AndroidKeyStoreSecretStore {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val sharedPreferences = EncryptedSharedPreferences.create(
                context,
                PREFERENCES_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            return AndroidKeyStoreSecretStore(sharedPreferences)
        }
    }
}
