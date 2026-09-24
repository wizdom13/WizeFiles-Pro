package com.wisso.wizefiles.core.entitlement.license

import com.wisso.wizefiles.core.entitlement.EntitlementState
import com.wisso.wizefiles.core.entitlement.EntitlementSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class VerifiedLicenseEntitlementSource(
    private val clock: () -> Long = System::currentTimeMillis,
) : EntitlementSource, LicenseEntitlementController {
    private val currentState = MutableStateFlow(EntitlementState.free())
    private var verifier: LicenseVerifier? = null
    private var cache: LicenseCache? = null

    override val state: StateFlow<EntitlementState> = currentState.asStateFlow()

    @Synchronized
    fun initialize(
        verifier: LicenseVerifier,
        cache: LicenseCache,
    ): LicenseVerificationResult? {
        this.verifier = verifier
        this.cache = cache
        return restore()
    }

    @Synchronized
    override fun installationId(): String? =
        runCatching { cache?.installationId() }.getOrNull()

    @Synchronized
    override fun accept(serializedDocument: String): LicenseVerificationResult {
        val activeVerifier = verifier
            ?: return rejected(LicenseRejectionReason.NOT_INITIALIZED)
        val activeCache = cache
            ?: return rejected(LicenseRejectionReason.NOT_INITIALIZED)
        val previous = runCatching { activeCache.read() }.getOrElse {
            return rejected(LicenseRejectionReason.STORAGE_FAILURE)
        }
        val trustedTime = previous?.trustedServerTimeEpochMillis ?: Long.MIN_VALUE
        val result = activeVerifier.verify(
            serializedDocument,
            maxOf(clock(), trustedTime),
        )
        if (result !is LicenseVerificationResult.Verified) return result
        if (result.claims.serverTimeEpochMillis.safeAdd(LicenseVerifier.DEFAULT_CLOCK_SKEW_MILLIS) < trustedTime) {
            return rejected(LicenseRejectionReason.CLOCK_ROLLBACK)
        }
        val cachedLicense = CachedSignedLicense(
            serializedDocument = serializedDocument,
            trustedServerTimeEpochMillis = maxOf(trustedTime, result.claims.serverTimeEpochMillis),
        )
        if (!runCatching { activeCache.write(cachedLicense) }.getOrDefault(false)) {
            return rejected(LicenseRejectionReason.STORAGE_FAILURE)
        }
        currentState.value = EntitlementState.of(result.claims.entitlements)
        return result
    }

    @Synchronized
    override fun restore(): LicenseVerificationResult? {
        val activeVerifier = verifier ?: return null
        val activeCache = cache ?: return null
        val cachedLicense = runCatching { activeCache.read() }.getOrElse {
            currentState.value = EntitlementState.free()
            return rejected(LicenseRejectionReason.STORAGE_FAILURE)
        } ?: run {
            currentState.value = EntitlementState.free()
            return null
        }
        val result = activeVerifier.verify(
            cachedLicense.serializedDocument,
            maxOf(clock(), cachedLicense.trustedServerTimeEpochMillis),
        )
        if (result is LicenseVerificationResult.Verified &&
            result.claims.serverTimeEpochMillis.safeAdd(LicenseVerifier.DEFAULT_CLOCK_SKEW_MILLIS) >=
            cachedLicense.trustedServerTimeEpochMillis
        ) {
            currentState.value = EntitlementState.of(result.claims.entitlements)
            return result
        }
        currentState.value = EntitlementState.free()
        runCatching { activeCache.clear() }
        return if (result is LicenseVerificationResult.Verified) {
            rejected(LicenseRejectionReason.CLOCK_ROLLBACK)
        } else {
            result
        }
    }

    @Synchronized
    override fun clear(): Boolean {
        val cleared = runCatching { cache?.clear() ?: true }.getOrDefault(false)
        currentState.value = EntitlementState.free()
        return cleared
    }

    private fun rejected(reason: LicenseRejectionReason) =
        LicenseVerificationResult.Rejected(reason)

    private fun Long.safeAdd(value: Long): Long =
        if (this > Long.MAX_VALUE - value) Long.MAX_VALUE else this + value
}
