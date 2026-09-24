package com.wisso.wizefiles.core.entitlement.license

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

@Suppress("DEPRECATION")
class AndroidEncryptedLicenseCache(
    context: Context,
) : LicenseCache {
    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        FILE_NAME,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    @Synchronized
    override fun installationId(): String {
        preferences.getString(KEY_INSTALLATION_ID, null)?.let { return it }
        val installationId = UUID.randomUUID().toString()
        check(preferences.edit().putString(KEY_INSTALLATION_ID, installationId).commit()) {
            "Unable to persist the WizeFiles license installation ID"
        }
        return installationId
    }

    @Synchronized
    override fun read(): CachedSignedLicense? {
        val document = preferences.getString(KEY_DOCUMENT, null) ?: return null
        if (!preferences.contains(KEY_TRUSTED_SERVER_TIME)) return null
        return CachedSignedLicense(
            serializedDocument = document,
            trustedServerTimeEpochMillis = preferences.getLong(KEY_TRUSTED_SERVER_TIME, 0L),
        )
    }

    @Synchronized
    override fun write(cachedLicense: CachedSignedLicense): Boolean =
        preferences.edit()
            .putString(KEY_DOCUMENT, cachedLicense.serializedDocument)
            .putLong(KEY_TRUSTED_SERVER_TIME, cachedLicense.trustedServerTimeEpochMillis)
            .commit()

    @Synchronized
    override fun clear(): Boolean =
        preferences.edit()
            .remove(KEY_DOCUMENT)
            .remove(KEY_TRUSTED_SERVER_TIME)
            .commit()

    companion object {
        private const val FILE_NAME = "wizefiles_verified_license"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEY_DOCUMENT = "signed_license_document"
        private const val KEY_TRUSTED_SERVER_TIME = "trusted_server_time_ms"
    }
}
