package com.wisso.wizefiles.feature.apksigning

/** Public APK signature schemes supported by the WizeFiles signing boundary. */
enum class ApkSignatureScheme {
    V1,
    V2,
    V3,
    V4
}

/**
 * A validated set of schemes for one signing operation.
 *
 * v4 is a detached idsig and therefore requires an embedded v2 or v3 signature. Every signing
 * operation must create at least one embedded signature.
 */
class ApkSignatureSelection private constructor(
    schemes: Set<ApkSignatureScheme>
) {
    val schemes: Set<ApkSignatureScheme> = schemes.toSet()

    val v1Enabled: Boolean get() = ApkSignatureScheme.V1 in schemes
    val v2Enabled: Boolean get() = ApkSignatureScheme.V2 in schemes
    val v3Enabled: Boolean get() = ApkSignatureScheme.V3 in schemes
    val v4Enabled: Boolean get() = ApkSignatureScheme.V4 in schemes

    override fun equals(other: Any?): Boolean =
        other is ApkSignatureSelection && schemes == other.schemes

    override fun hashCode(): Int = schemes.hashCode()

    override fun toString(): String = "ApkSignatureSelection($schemes)"

    companion object {
        fun of(vararg schemes: ApkSignatureScheme): ApkSignatureSelection = of(schemes.toSet())

        fun of(schemes: Set<ApkSignatureScheme>): ApkSignatureSelection {
            require(schemes.any { it != ApkSignatureScheme.V4 }) {
                "At least one embedded APK signature scheme is required"
            }
            require(ApkSignatureScheme.V4 !in schemes ||
                ApkSignatureScheme.V2 in schemes || ApkSignatureScheme.V3 in schemes) {
                "APK Signature Scheme v4 requires v2 or v3"
            }
            return ApkSignatureSelection(schemes)
        }
    }
}
