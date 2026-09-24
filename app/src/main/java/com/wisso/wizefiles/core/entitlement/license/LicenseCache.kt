package com.wisso.wizefiles.core.entitlement.license

data class CachedSignedLicense(
    val serializedDocument: String,
    val trustedServerTimeEpochMillis: Long,
)

interface LicenseCache {
    fun installationId(): String

    fun read(): CachedSignedLicense?

    fun write(cachedLicense: CachedSignedLicense): Boolean

    fun clear(): Boolean
}
