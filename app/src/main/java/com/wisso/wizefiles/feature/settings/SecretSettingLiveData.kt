// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.wisso.wizefiles.core.app.application
import com.wisso.wizefiles.security.SecretMigration
import com.wisso.wizefiles.security.SecretStore
import com.wisso.wizefiles.core.app.secretStore as globalSecretStore

class SecretSettingLiveData(
    @StringRes keyRes: Int,
    @StringRes defaultValueRes: Int,
    private val secretStoreProvider: () -> SecretStore = { globalSecretStore }
) : SettingLiveData<String>(keyRes, defaultValueRes) {

    private val secretKey: String = application.getString(keyRes)

    init {
        init()
    }

    override fun getDefaultValue(@StringRes defaultValueRes: Int): String =
        application.getString(defaultValueRes)

    override fun getValue(
        sharedPreferences: SharedPreferences,
        key: String,
        defaultValue: String
    ): String {
        val secretStore = secretStoreProvider()
        val legacyValue = sharedPreferences.getString(secretKey, null)
        return SecretMigration.migrateLegacyPreferenceSecret(
            key = secretKey,
            legacyValue = legacyValue,
            defaultValue = defaultValue,
            secretStore = secretStore,
            removeLegacyValue = { legacyKey ->
                sharedPreferences.edit { remove(legacyKey) }
            }
        )
    }

    override fun putValue(sharedPreferences: SharedPreferences, key: String, value: String) {
        val secretStore = secretStoreProvider()
        secretStore.putSecret(secretKey, value)
        sharedPreferences.edit { remove(secretKey) }
    }
}
