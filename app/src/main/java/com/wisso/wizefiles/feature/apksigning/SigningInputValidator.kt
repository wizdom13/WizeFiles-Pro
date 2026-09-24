package com.wisso.wizefiles.feature.apksigning

internal sealed interface SigningInputValidation {
    data class Valid(val minSdk: Int?) : SigningInputValidation
    data class Invalid(val error: ApkSigningUiValidationError) : SigningInputValidation
}

internal object SigningInputValidator {
    fun validate(schemes: Set<ApkSignatureScheme>, minimumSdk: String): SigningInputValidation {
        val error = validateApkSigningUiSelection(schemes, minimumSdk)
        return if (error == null) {
            SigningInputValidation.Valid(minimumSdk.trim().toIntOrNull())
        } else {
            SigningInputValidation.Invalid(error)
        }
    }

    fun verificationMinimumSdk(value: String): Int? {
        val trimmed = value.trim()
        require(trimmed.isEmpty() || trimmed.toIntOrNull()?.let { it > 0 } == true) {
            "Minimum SDK must be a positive integer"
        }
        return trimmed.toIntOrNull()
    }
}
