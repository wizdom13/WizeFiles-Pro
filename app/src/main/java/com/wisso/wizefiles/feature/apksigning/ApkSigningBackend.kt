// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import java.io.File
import java.io.IOException
import java.security.PrivateKey
import java.security.cert.X509Certificate

/** Private signing material already unlocked by the caller. Password handling lives above this seam. */
class ApkSigningKeyMaterial(
    val privateKey: PrivateKey,
    certificates: List<X509Certificate>
) {
    val certificates: List<X509Certificate> = certificates.toList()

    init {
        require(this.certificates.isNotEmpty()) { "A signing certificate is required" }
    }

    override fun toString(): String =
        "ApkSigningKeyMaterial(privateKey=[REDACTED], certificateCount=${certificates.size})"
}

data class ApkSigningRequest(
    val inputApk: File,
    val outputApk: File,
    val v4SignatureOutput: File? = null,
    val schemes: ApkSignatureSelection,
    val keyMaterial: ApkSigningKeyMaterial,
    val minSdkVersion: Int? = null
)

data class ApkVerificationRequest(
    val apk: File,
    val v4Signature: File? = null,
    val minSdkVersion: Int? = null
)

data class ApkSigningResult(
    val outputApk: File,
    val v4Signature: File?,
    val verification: ApkVerificationReport
)

/** Blocking file API. Provider-aware staging and dispatcher ownership are added by the workflow PR. */
interface ApkSigningBackend {
    @Throws(ApkSigningBackendException::class)
    fun sign(request: ApkSigningRequest): ApkSigningResult

    @Throws(ApkSigningBackendException::class)
    fun verify(request: ApkVerificationRequest): ApkVerificationReport
}

class ApkSigningBackendException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)
