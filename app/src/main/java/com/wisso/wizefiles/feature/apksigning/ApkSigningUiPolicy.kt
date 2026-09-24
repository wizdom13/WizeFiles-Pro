package com.wisso.wizefiles.feature.apksigning

internal enum class ApkSigningUiValidationError {
    EMBEDDED_SCHEME_REQUIRED,
    V4_REQUIRES_V2_OR_V3,
    INVALID_MINIMUM_SDK
}

internal fun validateApkSigningUiSelection(
    schemes: Set<ApkSignatureScheme>,
    minimumSdkText: String
): ApkSigningUiValidationError? {
    if (schemes.none { it != ApkSignatureScheme.V4 }) {
        return ApkSigningUiValidationError.EMBEDDED_SCHEME_REQUIRED
    }
    if (ApkSignatureScheme.V4 in schemes &&
        ApkSignatureScheme.V2 !in schemes &&
        ApkSignatureScheme.V3 !in schemes
    ) {
        return ApkSigningUiValidationError.V4_REQUIRES_V2_OR_V3
    }
    if (minimumSdkText.isNotBlank() && minimumSdkText.toIntOrNull()?.takeIf { it > 0 } == null) {
        return ApkSigningUiValidationError.INVALID_MINIMUM_SDK
    }
    return null
}

internal fun signedApkFileName(sourceName: String): String {
    val baseName = sourceName.removeSuffix(".apk").ifBlank { "app" }
    return "$baseName-signed.apk"
}
