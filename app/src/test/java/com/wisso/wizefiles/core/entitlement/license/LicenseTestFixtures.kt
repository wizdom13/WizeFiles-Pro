package com.wisso.wizefiles.core.entitlement.license

import com.wisso.wizefiles.core.entitlement.Entitlement
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

internal const val TEST_KEY_ID = "test-2026"
internal const val TEST_PACKAGE = "com.wisso.wizefiles"
internal const val TEST_INSTALLATION = "2f9f589f-cf98-47d9-a07d-eb7750dfd2d2"
internal const val TEST_NOW = 1_800_000_000_000L

internal fun testKeyPair(): KeyPair =
    KeyPairGenerator.getInstance(LicenseVerifier.SIGNATURE_ALGORITHM).generateKeyPair()

internal fun testClaims(
    serverTime: Long = TEST_NOW,
    validUntil: Long = TEST_NOW + 30L * 24L * 60L * 60L * 1000L,
    packageName: String = TEST_PACKAGE,
    installationId: String = TEST_INSTALLATION,
    entitlements: Set<Entitlement> = setOf(Entitlement.PRO),
): LicenseClaims =
    LicenseClaims(
        schemaVersion = LicenseVerifier.SUPPORTED_SCHEMA_VERSION,
        licenseId = "license-123",
        packageName = packageName,
        installationId = installationId,
        entitlements = entitlements,
        issuedAtEpochMillis = serverTime - 1_000L,
        notBeforeEpochMillis = serverTime - 1_000L,
        refreshAfterEpochMillis = minOf(serverTime + 24L * 60L * 60L * 1000L, validUntil),
        validUntilEpochMillis = validUntil,
        serverTimeEpochMillis = serverTime,
    )

internal fun signLicense(
    claims: LicenseClaims,
    keyPair: KeyPair,
    keyId: String = TEST_KEY_ID,
): String {
    val payloadBytes = licensePayloadJson(claims).toString().toByteArray(Charsets.UTF_8)
    val signatureBytes = Signature.getInstance(LicenseVerifier.SIGNATURE_ALGORITHM).run {
        initSign(keyPair.private)
        update(payloadBytes)
        sign()
    }
    val encoder = Base64.getUrlEncoder().withoutPadding()
    return SignedLicenseDocument(
        keyId = keyId,
        payload = encoder.encodeToString(payloadBytes),
        signature = encoder.encodeToString(signatureBytes),
    ).serialize()
}

internal fun testVerifier(
    keyPair: KeyPair,
    packageName: String = TEST_PACKAGE,
    installationId: String = TEST_INSTALLATION,
): LicenseVerifier =
    LicenseVerifier(
        publicKeys = PinnedLicensePublicKeyProvider.fromKeys(
            mapOf(TEST_KEY_ID to keyPair.public),
        ),
        expectedPackageName = packageName,
        expectedInstallationId = installationId,
    )
