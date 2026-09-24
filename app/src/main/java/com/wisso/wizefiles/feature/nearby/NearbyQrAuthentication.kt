package com.wisso.wizefiles.feature.nearby

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

internal object NearbyQrAuthentication {
    private const val PREFIX = "WZF-NEARBY:1:"
    private const val PROOF_BYTES = 32
    private val domain = "WizeFiles Nearby QR v1\u0000"
        .toByteArray(StandardCharsets.UTF_8)

    fun encode(rawAuthenticationToken: ByteArray): String {
        require(rawAuthenticationToken.isNotEmpty()) { "Authentication token is empty" }
        return PREFIX + Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(proof(rawAuthenticationToken))
    }

    fun matches(rawAuthenticationToken: ByteArray, scannedQr: String): Boolean {
        if (rawAuthenticationToken.isEmpty() || !scannedQr.startsWith(PREFIX)) return false
        val encodedProof = scannedQr.removePrefix(PREFIX)
        val scannedProof = runCatching {
            Base64.getUrlDecoder().decode(encodedProof)
        }.getOrNull() ?: return false
        if (scannedProof.size != PROOF_BYTES) return false
        return MessageDigest.isEqual(proof(rawAuthenticationToken), scannedProof)
    }

    private fun proof(rawAuthenticationToken: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").run {
            update(domain)
            digest(rawAuthenticationToken)
        }
}
