package com.wisso.wizefiles.feature.apksigning

import java.io.File
import java.io.IOException

enum class AabSigningKeySource {
    KEY_STORE,
    PKCS8_CERTIFICATE
}

data class AabSigningRequest(
    val inputAab: File,
    val outputAab: File,
    val keyMaterial: ApkSigningKeyMaterial
)

data class AabVerificationRequest(val aab: File)

data class AabSignerCertificate(
    val sha256: String,
    val subject: String,
    val issuer: String,
    val notBeforeMillis: Long,
    val notAfterMillis: Long
)

data class AabVerificationReport(
    val verified: Boolean,
    val signerCertificates: List<AabSignerCertificate>,
    val signedEntryCount: Int,
    val errors: List<String>,
    val warnings: List<String>
)

data class AabSigningResult(
    val outputAab: File,
    val verification: AabVerificationReport
)

interface AabSigningBackend {
    @Throws(AabSigningBackendException::class)
    fun sign(request: AabSigningRequest): AabSigningResult

    @Throws(AabSigningBackendException::class)
    fun verify(request: AabVerificationRequest): AabVerificationReport
}

class AabSigningBackendException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)
