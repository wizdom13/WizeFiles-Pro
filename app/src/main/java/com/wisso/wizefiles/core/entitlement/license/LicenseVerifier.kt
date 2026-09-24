package com.wisso.wizefiles.core.entitlement.license

import java.security.KeyFactory
import java.security.Provider
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.bouncycastle.jce.provider.BouncyCastleProvider

private val licenseCryptoProvider: Provider by lazy(::BouncyCastleProvider)

fun interface LicensePublicKeyProvider {
    fun find(keyId: String): PublicKey?
}

class PinnedLicensePublicKeyProvider private constructor(
    private val keys: Map<String, PublicKey>,
) : LicensePublicKeyProvider {
    override fun find(keyId: String): PublicKey? = keys[keyId]

    companion object {
        fun empty(): PinnedLicensePublicKeyProvider = PinnedLicensePublicKeyProvider(emptyMap())

        fun fromX509Base64(keyId: String, encodedKey: String): PinnedLicensePublicKeyProvider {
            if (keyId.isBlank() && encodedKey.isBlank()) return empty()
            require(keyId.isNotBlank() && encodedKey.isNotBlank()) {
                "Both the license key ID and public key must be configured"
            }
            val keyBytes = Base64.getDecoder().decode(encodedKey)
            val publicKey = KeyFactory.getInstance(
                LicenseVerifier.SIGNATURE_ALGORITHM,
                licenseCryptoProvider,
            )
                .generatePublic(X509EncodedKeySpec(keyBytes))
            return PinnedLicensePublicKeyProvider(mapOf(keyId to publicKey))
        }

        internal fun fromKeys(keys: Map<String, PublicKey>): PinnedLicensePublicKeyProvider =
            PinnedLicensePublicKeyProvider(keys.toMap())
    }
}

enum class LicenseRejectionReason {
    MALFORMED_DOCUMENT,
    UNKNOWN_KEY,
    INVALID_SIGNATURE,
    UNSUPPORTED_SCHEMA,
    PACKAGE_MISMATCH,
    INSTALLATION_MISMATCH,
    INVALID_TIME_WINDOW,
    NOT_YET_VALID,
    EXPIRED,
    CLOCK_ROLLBACK,
    STORAGE_FAILURE,
    NOT_INITIALIZED,
    UNSUPPORTED_BUILD,
}

sealed interface LicenseVerificationResult {
    data class Verified(val claims: LicenseClaims) : LicenseVerificationResult

    data class Rejected(val reason: LicenseRejectionReason) : LicenseVerificationResult
}

class LicenseVerifier(
    private val publicKeys: LicensePublicKeyProvider,
    private val expectedPackageName: String,
    private val expectedInstallationId: String,
    private val allowedClockSkewMillis: Long = DEFAULT_CLOCK_SKEW_MILLIS,
) {
    fun verify(
        serializedDocument: String,
        nowEpochMillis: Long,
    ): LicenseVerificationResult {
        val document = SignedLicenseDocument.parse(serializedDocument)
            ?: return rejected(LicenseRejectionReason.MALFORMED_DOCUMENT)
        val payloadBytes = LicenseClaimsParser.decodePayload(document.payload)
            ?: return rejected(LicenseRejectionReason.MALFORMED_DOCUMENT)
        val signatureBytes = runCatching {
            Base64.getUrlDecoder().decode(document.signature)
        }.getOrNull() ?: return rejected(LicenseRejectionReason.MALFORMED_DOCUMENT)
        val publicKey = publicKeys.find(document.keyId)
            ?: return rejected(LicenseRejectionReason.UNKNOWN_KEY)
        val signatureValid = runCatching {
            Signature.getInstance(SIGNATURE_ALGORITHM, licenseCryptoProvider).run {
                initVerify(publicKey)
                update(payloadBytes)
                verify(signatureBytes)
            }
        }.getOrDefault(false)
        if (!signatureValid) return rejected(LicenseRejectionReason.INVALID_SIGNATURE)

        val claims = LicenseClaimsParser.parse(payloadBytes)
            ?: return rejected(LicenseRejectionReason.MALFORMED_DOCUMENT)
        if (claims.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return rejected(LicenseRejectionReason.UNSUPPORTED_SCHEMA)
        }
        if (claims.packageName != expectedPackageName) {
            return rejected(LicenseRejectionReason.PACKAGE_MISMATCH)
        }
        if (claims.installationId != expectedInstallationId) {
            return rejected(LicenseRejectionReason.INSTALLATION_MISMATCH)
        }
        if (!claims.hasValidTimeWindow()) {
            return rejected(LicenseRejectionReason.INVALID_TIME_WINDOW)
        }
        val trustedNow = maxOf(nowEpochMillis, claims.serverTimeEpochMillis)
        if (claims.notBeforeEpochMillis > trustedNow.safeAdd(allowedClockSkewMillis)) {
            return rejected(LicenseRejectionReason.NOT_YET_VALID)
        }
        if (trustedNow >= claims.validUntilEpochMillis) {
            return rejected(LicenseRejectionReason.EXPIRED)
        }
        return LicenseVerificationResult.Verified(claims)
    }

    private fun LicenseClaims.hasValidTimeWindow(): Boolean =
        issuedAtEpochMillis <= serverTimeEpochMillis.safeAdd(allowedClockSkewMillis) &&
            notBeforeEpochMillis < validUntilEpochMillis &&
            issuedAtEpochMillis <= refreshAfterEpochMillis &&
            refreshAfterEpochMillis <= validUntilEpochMillis &&
            serverTimeEpochMillis < validUntilEpochMillis

    private fun rejected(reason: LicenseRejectionReason) =
        LicenseVerificationResult.Rejected(reason)

    private fun Long.safeAdd(value: Long): Long =
        if (this > Long.MAX_VALUE - value) Long.MAX_VALUE else this + value

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val DEFAULT_CLOCK_SKEW_MILLIS = 5L * 60L * 1000L
        internal const val SIGNATURE_ALGORITHM = "Ed25519"
    }
}
