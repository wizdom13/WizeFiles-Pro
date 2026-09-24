// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import android.content.Context
import com.wisso.wizefiles.R
import com.wisso.wizefiles.feature.filejobs.FileOperationService
import java.nio.file.Path

internal enum class SplitSetSigningMode { SIGN, VERIFY }

data class SplitSetSigningStrings(
    val resumeTitle: Int,
    val signTitle: Int,
    val verifyTitle: Int,
    val signHeading: Int,
    val verifyHeading: Int,
    val signExplanation: Int,
    val verifyExplanation: Int,
    val sectionInputOutput: Int,
    val input: Int,
    val chooseInput: Int,
    val output: Int,
    val sectionKey: Int,
    val noV4: Int,
    val sectionReview: Int,
    val start: Int,
    val sectionVerify: Int,
    val verify: Int,
    val resumeUnavailable: Int,
    val resumeUnavailableExplanation: Int,
    val reenterPassword: Int,
    val resumeExplanation: Int,
    val resumeAction: Int,
    val chooseInputFirst: Int,
    val chooseOutputFirst: Int,
    val invalidRequest: Int,
    val started: Int,
    val startFailed: Int,
    val resumed: Int,
    val resumeFailed: Int,
    val verificationFailedToRun: Int,
    val packageName: Int,
    val version: Int,
    val apkCount: Int
)

internal data class SplitSetSigningResumeSpec(
    val sourceUri: String,
    val outputUri: String,
    val signingKeyUri: String,
    val keySource: AabSigningKeySource
)

internal data class SplitSetSigningRequest(
    val sourceUri: String,
    val outputUri: String,
    val signingKeyUri: String,
    val certificateUri: String,
    val keySource: AabSigningKeySource,
    val keyAlias: String,
    val keyStoreFormat: ApkKeyStoreFormat,
    val schemes: Set<ApkSignatureScheme>,
    val conflictPolicy: ApkSigningOutputConflictPolicy
) {
    init {
        require(sourceUri.isNotBlank())
        require(outputUri.isNotBlank())
        require(signingKeyUri.isNotBlank())
        require(sourceUri != outputUri)
        require(ApkSignatureScheme.V4 !in schemes)
        ApkSignatureSelection.of(schemes)
        if (keySource == AabSigningKeySource.PKCS8_CERTIFICATE) {
            require(certificateUri.isNotBlank())
        }
    }
}

enum class SplitSetPackageKind(
    val extension: String,
    val strings: SplitSetSigningStrings
) {
    APKS(
        extension = "apks",
        strings = SplitSetSigningStrings(
            resumeTitle = R.string.apks_signing_resume_title,
            signTitle = R.string.apks_signing_sign_title,
            verifyTitle = R.string.apks_signing_verify_title,
            signHeading = R.string.apks_signing_sign_heading,
            verifyHeading = R.string.apks_signing_verify_heading,
            signExplanation = R.string.apks_signing_sign_explanation,
            verifyExplanation = R.string.apks_signing_verify_explanation,
            sectionInputOutput = R.string.apks_signing_section_input_output,
            input = R.string.apks_signing_input,
            chooseInput = R.string.apks_signing_choose_input,
            output = R.string.apks_signing_output,
            sectionKey = R.string.apks_signing_section_key,
            noV4 = R.string.apks_signing_no_v4,
            sectionReview = R.string.apks_signing_section_review,
            start = R.string.apks_signing_start,
            sectionVerify = R.string.apks_signing_section_verify,
            verify = R.string.apks_signing_verify,
            resumeUnavailable = R.string.apks_signing_resume_unavailable,
            resumeUnavailableExplanation = R.string.apks_signing_resume_unavailable_explanation,
            reenterPassword = R.string.apks_signing_reenter_password,
            resumeExplanation = R.string.apks_signing_resume_explanation,
            resumeAction = R.string.apks_signing_resume_action,
            chooseInputFirst = R.string.apks_signing_choose_input_first,
            chooseOutputFirst = R.string.apks_signing_choose_output_first,
            invalidRequest = R.string.apks_signing_invalid_request,
            started = R.string.apks_signing_started,
            startFailed = R.string.apks_signing_start_failed,
            resumed = R.string.apks_signing_resumed,
            resumeFailed = R.string.apks_signing_resume_failed,
            verificationFailedToRun = R.string.apks_signing_verification_failed_to_run,
            packageName = R.string.apks_signing_package,
            version = R.string.apks_signing_version,
            apkCount = R.string.apks_signing_apk_count
        )
    ),
    XAPK(
        extension = "xapk",
        strings = SplitSetSigningStrings(
            resumeTitle = R.string.xapk_signing_resume_title,
            signTitle = R.string.xapk_signing_sign_title,
            verifyTitle = R.string.xapk_signing_verify_title,
            signHeading = R.string.xapk_signing_sign_heading,
            verifyHeading = R.string.xapk_signing_verify_heading,
            signExplanation = R.string.xapk_signing_sign_explanation,
            verifyExplanation = R.string.xapk_signing_verify_explanation,
            sectionInputOutput = R.string.xapk_signing_section_input_output,
            input = R.string.xapk_signing_input,
            chooseInput = R.string.xapk_signing_choose_input,
            output = R.string.xapk_signing_output,
            sectionKey = R.string.xapk_signing_section_key,
            noV4 = R.string.xapk_signing_no_v4,
            sectionReview = R.string.xapk_signing_section_review,
            start = R.string.xapk_signing_start,
            sectionVerify = R.string.xapk_signing_section_verify,
            verify = R.string.xapk_signing_verify,
            resumeUnavailable = R.string.xapk_signing_resume_unavailable,
            resumeUnavailableExplanation = R.string.xapk_signing_resume_unavailable_explanation,
            reenterPassword = R.string.xapk_signing_reenter_password,
            resumeExplanation = R.string.xapk_signing_resume_explanation,
            resumeAction = R.string.xapk_signing_resume_action,
            chooseInputFirst = R.string.xapk_signing_choose_input_first,
            chooseOutputFirst = R.string.xapk_signing_choose_output_first,
            invalidRequest = R.string.xapk_signing_invalid_request,
            started = R.string.xapk_signing_started,
            startFailed = R.string.xapk_signing_start_failed,
            resumed = R.string.xapk_signing_resumed,
            resumeFailed = R.string.xapk_signing_resume_failed,
            verificationFailedToRun = R.string.xapk_signing_verification_failed_to_run,
            packageName = R.string.xapk_signing_package,
            version = R.string.xapk_signing_version,
            apkCount = R.string.xapk_signing_apk_count
        )
    );

    internal fun load(operationId: String): SplitSetSigningResumeSpec? = when (this) {
        APKS -> ApksSigningOperationStore.load(operationId)?.let {
            SplitSetSigningResumeSpec(it.sourceUri, it.outputUri, it.signingKeyUri, it.keySource)
        }
        XAPK -> XapkSigningOperationStore.load(operationId)?.let {
            SplitSetSigningResumeSpec(it.sourceUri, it.outputUri, it.signingKeyUri, it.keySource)
        }
    }

    internal fun start(
        request: SplitSetSigningRequest,
        secrets: ApkSigningSecrets,
        context: Context
    ): String = when (this) {
        APKS -> FileOperationService.signApks(
            ApksSigningWorkflowSpec(
                sourceUri = request.sourceUri,
                outputUri = request.outputUri,
                signingKeyUri = request.signingKeyUri,
                certificateUri = request.certificateUri,
                keySource = request.keySource,
                keyAlias = request.keyAlias,
                keyStoreFormat = request.keyStoreFormat,
                schemes = request.schemes,
                conflictPolicy = request.conflictPolicy
            ),
            secrets,
            context
        )
        XAPK -> FileOperationService.signXapk(
            XapkSigningWorkflowSpec(
                sourceUri = request.sourceUri,
                outputUri = request.outputUri,
                signingKeyUri = request.signingKeyUri,
                certificateUri = request.certificateUri,
                keySource = request.keySource,
                keyAlias = request.keyAlias,
                keyStoreFormat = request.keyStoreFormat,
                schemes = request.schemes,
                conflictPolicy = request.conflictPolicy
            ),
            secrets,
            context
        )
    }

    internal fun resume(operationId: String, secrets: ApkSigningSecrets, context: Context): Boolean =
        when (this) {
            APKS -> FileOperationService.resumeApksSigning(operationId, secrets, context)
            XAPK -> FileOperationService.resumeXapkSigning(operationId, secrets, context)
        }

    internal fun verify(path: Path): AndroidSplitSetVerificationReport = when (this) {
        APKS -> ProviderApksVerifier().verify(path)
        XAPK -> ProviderXapkVerifier().verify(path)
    }

    internal fun signedName(name: String): String {
        val suffix = ".$extension"
        val base = name.ifBlank { "packages$suffix" }
            .removeSuffix(suffix)
            .removeSuffix(suffix.uppercase())
        return "$base-signed$suffix"
    }
}
