package com.wisso.wizefiles.feature.apksigning

import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** WizeFiles-owned boundary around the pinned Android-compatible apksig port. */
class ApksigApkSigningBackend : ApkSigningBackend {
    override fun sign(request: ApkSigningRequest): ApkSigningResult {
        validateSigningRequest(request)

        try {
            val signerConfig = ApkSigner.SignerConfig.Builder(
                V1_SIGNER_NAME,
                request.keyMaterial.privateKey,
                request.keyMaterial.certificates
            ).build()
            val builder = ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(request.inputApk)
                .setOutputApk(request.outputApk)
                .setV1SigningEnabled(request.schemes.v1Enabled)
                .setV2SigningEnabled(request.schemes.v2Enabled)
                .setV3SigningEnabled(request.schemes.v3Enabled)
                .setV4SigningEnabled(request.schemes.v4Enabled)
                .setOtherSignersSignaturesPreserved(false)
                .setCreatedBy(CREATED_BY)

            request.minSdkVersion?.let(builder::setMinSdkVersion)
            request.v4SignatureOutput?.let(builder::setV4SignatureOutputFile)
            builder.build().sign()

            val verification = verify(
                ApkVerificationRequest(
                    request.outputApk,
                    request.v4SignatureOutput,
                    request.minSdkVersion
                )
            )
            val missingSchemes = request.schemes.schemes - verification.verifiedSchemes
            val expectedCertificate = sha256(request.keyMaterial.certificates.first().encoded)
            if (!verification.verified || missingSchemes.isNotEmpty() ||
                expectedCertificate !in verification.signerCertificateSha256) {
                throw ApkSigningBackendException(
                    buildString {
                        append("Signed APK verification failed")
                        if (missingSchemes.isNotEmpty()) append(": missing $missingSchemes")
                        if (expectedCertificate !in verification.signerCertificateSha256) {
                            append(": signer certificate mismatch")
                        }
                    }
                )
            }
            return ApkSigningResult(
                request.outputApk,
                request.v4SignatureOutput,
                verification
            )
        } catch (exception: Exception) {
            request.outputApk.delete()
            request.v4SignatureOutput?.delete()
            if (exception is ApkSigningBackendException) throw exception
            throw ApkSigningBackendException("Unable to sign APK", exception)
        }
    }

    override fun verify(request: ApkVerificationRequest): ApkVerificationReport {
        requireReadableFile(request.apk, "APK")
        request.v4Signature?.let { requireReadableFile(it, "v4 signature") }
        validateMinSdk(request.minSdkVersion)

        try {
            val overall = verifierBuilder(request.apk, request.v4Signature)
                .apply {
                    request.minSdkVersion?.let { setMinCheckedPlatformVersion(it) }
                }
                .build()
                .verify()
            val verifiedSchemes = ApkSignatureScheme.entries.filterTo(linkedSetOf()) { scheme ->
                probeScheme(request.apk, request.v4Signature, scheme)
            }
            return ApkVerificationReport(
                verified = overall.isVerified,
                verifiedSchemes = verifiedSchemes,
                signerCertificateSha256 = overall.signerCertificates
                    .map { sha256(it.encoded) }
                    .distinct(),
                errors = overall.errors.map(::issue),
                warnings = overall.warnings.map(::issue)
            )
        } catch (exception: Exception) {
            if (exception is ApkSigningBackendException) throw exception
            throw ApkSigningBackendException("Unable to verify APK signatures", exception)
        }
    }

    private fun probeScheme(
        apk: File,
        v4Signature: File?,
        scheme: ApkSignatureScheme
    ): Boolean {
        if (scheme == ApkSignatureScheme.V4 && v4Signature == null) return false
        return try {
            val builder = verifierBuilder(apk, if (scheme == ApkSignatureScheme.V4) {
                v4Signature
            } else {
                null
            })
            when (scheme) {
                ApkSignatureScheme.V1 -> builder
                    // The generated APK declares minSdk 21. Checking below the manifest floor
                    // makes apksig reject the APK before it can report the v1 signer.
                    .setMinCheckedPlatformVersion(21)
                    .setMaxCheckedPlatformVersion(23)
                    .build().verify().isVerifiedUsingV1Scheme
                ApkSignatureScheme.V2 -> builder
                    .setMinCheckedPlatformVersion(24)
                    .setMaxCheckedPlatformVersion(27)
                    .build().verify().isVerifiedUsingV2Scheme
                ApkSignatureScheme.V3 -> builder
                    .setMinCheckedPlatformVersion(28)
                    .build().verify().isVerifiedUsingV3Scheme
                ApkSignatureScheme.V4 -> builder
                    .setMinCheckedPlatformVersion(28)
                    .build().verify().isVerifiedUsingV4Scheme
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun verifierBuilder(apk: File, v4Signature: File?): ApkVerifier.Builder =
        ApkVerifier.Builder(apk).apply {
            v4Signature?.let { setV4SignatureFile(it) }
        }

    private fun validateSigningRequest(request: ApkSigningRequest) {
        requireReadableFile(request.inputApk, "Input APK")
        validateMinSdk(request.minSdkVersion)
        require(request.inputApk.canonicalFile != request.outputApk.canonicalFile) {
            "The original APK cannot be overwritten"
        }
        require(!request.outputApk.exists()) { "Output APK already exists" }
        require(request.outputApk.parentFile?.isDirectory == true) {
            "Output APK directory does not exist"
        }
        require(request.schemes.v4Enabled == (request.v4SignatureOutput != null)) {
            "A detached v4 output is required if and only if v4 is enabled"
        }
        request.v4SignatureOutput?.let { v4Output ->
            require(v4Output.canonicalFile != request.inputApk.canonicalFile &&
                v4Output.canonicalFile != request.outputApk.canonicalFile) {
                "The v4 signature must use a separate output file"
            }
            require(!v4Output.exists()) { "v4 signature output already exists" }
            require(v4Output.parentFile?.isDirectory == true) {
                "v4 signature output directory does not exist"
            }
        }
    }

    private fun requireReadableFile(file: File, label: String) {
        require(file.isFile && file.canRead()) { "$label is not a readable regular file" }
    }

    private fun validateMinSdk(minSdkVersion: Int?) {
        require(minSdkVersion == null || minSdkVersion > 0) {
            "Minimum SDK version must be positive"
        }
    }

    private fun issue(issue: ApkVerifier.IssueWithParams): ApkVerificationIssue =
        ApkVerificationIssue(issue.issue.name, issue.toString())

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            String.format(Locale.ROOT, "%02X", it.toInt() and 0xFF)
        }

    private companion object {
        const val V1_SIGNER_NAME = "WIZEFILE"
        const val CREATED_BY = "WizeFiles"
    }
}
