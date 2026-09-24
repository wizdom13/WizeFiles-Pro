package com.wisso.wizefiles.core.entitlement

import android.content.Context
import com.wisso.wizefiles.BuildConfig
import com.wisso.wizefiles.core.entitlement.license.AndroidEncryptedLicenseCache
import com.wisso.wizefiles.core.entitlement.license.LicenseEntitlementController
import com.wisso.wizefiles.core.entitlement.license.LicenseVerifier
import com.wisso.wizefiles.core.entitlement.license.PinnedLicensePublicKeyProvider
import com.wisso.wizefiles.core.entitlement.license.VerifiedLicenseEntitlementSource
import com.wisso.wizefiles.util.AppLog

/**
 * Release entitlement state is derived only from a backend-signed license document.
 */
object AppEntitlements {
    private val source = VerifiedLicenseEntitlementSource()

    val repository: EntitlementRepository = DefaultEntitlementRepository(source)
    val licenseController: LicenseEntitlementController = source

    fun initialize(context: Context) {
        runCatching {
            val cache = AndroidEncryptedLicenseCache(context)
            val publicKeys = PinnedLicensePublicKeyProvider.fromX509Base64(
                BuildConfig.LICENSE_KEY_ID,
                BuildConfig.LICENSE_PUBLIC_KEY_X509_BASE64,
            )
            source.initialize(
                verifier = LicenseVerifier(
                    publicKeys = publicKeys,
                    expectedPackageName = context.packageName,
                    expectedInstallationId = cache.installationId(),
                ),
                cache = cache,
            )
        }.onFailure { error ->
            source.clear()
            AppLog.e(TAG, "Unable to initialize verified license state", error)
        }
    }

    private const val TAG = "AppEntitlements"
}
