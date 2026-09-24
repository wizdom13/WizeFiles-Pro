// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.sftp.client

import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.util.valueCompat
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

data class SftpPresentedHostKey(
    val algorithm: String,
    val encodedKeyBase64: String,
    val sha256Fingerprint: String
) {
    fun encodedKeyBytes(): ByteArray = Base64.getDecoder().decode(encodedKeyBase64)

    companion object {
        fun from(publicKey: PublicKey): SftpPresentedHostKey {
            val encoded = publicKey.encoded ?: ByteArray(0)
            return SftpPresentedHostKey(
                algorithm = publicKey.algorithm,
                encodedKeyBase64 = Base64.getEncoder().encodeToString(encoded),
                sha256Fingerprint = sha256Fingerprint(encoded)
            )
        }

        private fun sha256Fingerprint(encodedKey: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(encodedKey)
            val base64Digest = Base64.getEncoder().withoutPadding().encodeToString(digest)
            return "SHA256:$base64Digest"
        }
    }
}

data class PinnedSftpHostKey(
    val algorithm: String,
    val encodedKeyBase64: String,
    val sha256Fingerprint: String
) {
    fun matches(presented: SftpPresentedHostKey): Boolean =
        algorithm == presented.algorithm && encodedKeyBase64 == presented.encodedKeyBase64

    companion object {
        fun from(presented: SftpPresentedHostKey): PinnedSftpHostKey = PinnedSftpHostKey(
            algorithm = presented.algorithm,
            encodedKeyBase64 = presented.encodedKeyBase64,
            sha256Fingerprint = presented.sha256Fingerprint
        )
    }
}

sealed class SftpHostKeyVerificationException(
    open val host: String,
    open val port: Int,
    open val algorithm: String,
    open val presentedSha256Fingerprint: String,
    open val presentedEncodedKeyBase64: String,
    message: String
) : Exception(message)

class SftpUnknownHostKeyException(
    override val host: String,
    override val port: Int,
    override val algorithm: String,
    override val presentedSha256Fingerprint: String,
    override val presentedEncodedKeyBase64: String
) : SftpHostKeyVerificationException(
    host = host,
    port = port,
    algorithm = algorithm,
    presentedSha256Fingerprint = presentedSha256Fingerprint,
    presentedEncodedKeyBase64 = presentedEncodedKeyBase64,
    message = "SFTP host key for $host:$port is not trusted yet. " +
        "Open and edit this SFTP connection, verify the fingerprint, and explicitly trust it."
)

class SftpHostKeyMismatchException(
    override val host: String,
    override val port: Int,
    override val algorithm: String,
    override val presentedSha256Fingerprint: String,
    val expectedSha256Fingerprint: String,
    override val presentedEncodedKeyBase64: String
) : SftpHostKeyVerificationException(
    host = host,
    port = port,
    algorithm = algorithm,
    presentedSha256Fingerprint = presentedSha256Fingerprint,
    presentedEncodedKeyBase64 = presentedEncodedKeyBase64,
    message = "SFTP host key mismatch for $host:$port. " +
        "Expected $expectedSha256Fingerprint but got $presentedSha256Fingerprint."
)

class SftpHostKeyTrustStore(
    private val getRawStore: () -> String,
    private val setRawStore: (String) -> Unit
) {
    private val trustedOnceStore = ConcurrentHashMap<String, PinnedSftpHostKey>()

    fun getPinnedHostKey(host: String, port: Int): PinnedSftpHostKey? {
        val map = parseStore(getRawStore())
        return map[normalizeEndpoint(host, port)]
    }

    fun getTrustedHostKey(host: String, port: Int): PinnedSftpHostKey? {
        val endpoint = normalizeEndpoint(host, port)
        return trustedOnceStore[endpoint] ?: getPinnedHostKey(host, port)
    }

    fun trustHostKeyOnce(host: String, port: Int, presentedHostKey: SftpPresentedHostKey) {
        trustedOnceStore[normalizeEndpoint(host, port)] = PinnedSftpHostKey.from(presentedHostKey)
    }

    fun trustHostKey(host: String, port: Int, presentedHostKey: SftpPresentedHostKey) {
        val map = parseStore(getRawStore()).toMutableMap()
        map[normalizeEndpoint(host, port)] = PinnedSftpHostKey.from(presentedHostKey)
        setRawStore(serializeStore(map))
    }

    internal fun parseStore(raw: String): Map<String, PinnedSftpHostKey> {
        if (raw.isBlank()) {
            return emptyMap()
        }
        val json = try {
            JSONObject(raw)
        } catch (_: JSONException) {
            return emptyMap()
        }
        val result = mutableMapOf<String, PinnedSftpHostKey>()
        json.keys().forEach { endpoint ->
            val value = json.optJSONObject(endpoint) ?: return@forEach
            val algorithm = value.optString(FIELD_ALGORITHM)
            val encodedKeyBase64 = value.optString(FIELD_ENCODED_KEY_BASE64)
            val sha256Fingerprint = value.optString(FIELD_SHA256_FINGERPRINT)
            if (algorithm.isBlank() || encodedKeyBase64.isBlank() || sha256Fingerprint.isBlank()) {
                return@forEach
            }
            result[endpoint] = PinnedSftpHostKey(
                algorithm = algorithm,
                encodedKeyBase64 = encodedKeyBase64,
                sha256Fingerprint = sha256Fingerprint
            )
        }
        return result
    }

    internal fun serializeStore(store: Map<String, PinnedSftpHostKey>): String {
        val json = JSONObject()
        store.forEach { (endpoint, pinnedKey) ->
            json.put(
                endpoint,
                JSONObject()
                    .put(FIELD_ALGORITHM, pinnedKey.algorithm)
                    .put(FIELD_ENCODED_KEY_BASE64, pinnedKey.encodedKeyBase64)
                    .put(FIELD_SHA256_FINGERPRINT, pinnedKey.sha256Fingerprint)
            )
        }
        return json.toString()
    }

    fun normalizeEndpoint(host: String, port: Int): String = "${host.trim().lowercase()}:$port"

    companion object {
        private const val FIELD_ALGORITHM = "algorithm"
        private const val FIELD_ENCODED_KEY_BASE64 = "encodedKeyBase64"
        private const val FIELD_SHA256_FINGERPRINT = "sha256Fingerprint"

        val appInstance: SftpHostKeyTrustStore by lazy {
            SftpHostKeyTrustStore(
                getRawStore = {
                    Settings.SFTP_PINNED_HOST_KEYS.valueCompat
                        ?: JSONObject().toString()
                },
                setRawStore = Settings.SFTP_PINNED_HOST_KEYS::putValue
            )
        }
    }
}

class PinnedSftpHostKeyVerifier(
    private val trustStore: SftpHostKeyTrustStore
) : HostKeyVerifier {
    private val lastFailure = AtomicReference<SftpHostKeyVerificationException?>(null)

    override fun verify(host: String, port: Int, key: PublicKey): Boolean {
        val presented = SftpPresentedHostKey.from(key)
        val pinned = trustStore.getTrustedHostKey(host, port)
        if (pinned == null) {
            lastFailure.set(
                SftpUnknownHostKeyException(
                    host = host,
                    port = port,
                    algorithm = presented.algorithm,
                    presentedSha256Fingerprint = presented.sha256Fingerprint,
                    presentedEncodedKeyBase64 = presented.encodedKeyBase64
                )
            )
            return false
        }
        if (!pinned.matches(presented)) {
            lastFailure.set(
                SftpHostKeyMismatchException(
                    host = host,
                    port = port,
                    algorithm = presented.algorithm,
                    expectedSha256Fingerprint = pinned.sha256Fingerprint,
                    presentedSha256Fingerprint = presented.sha256Fingerprint,
                    presentedEncodedKeyBase64 = presented.encodedKeyBase64
                )
            )
            return false
        }
        lastFailure.set(null)
        return true
    }

    override fun findExistingAlgorithms(host: String, port: Int): MutableList<String> {
        val pinned = trustStore.getPinnedHostKey(host, port) ?: return mutableListOf()
        return mutableListOf(pinned.algorithm)
    }

    fun consumeFailure(): SftpHostKeyVerificationException? {
        return lastFailure.getAndSet(null)
    }
}

internal object SftpHostKeyFailureParser {
    private val hostPortPattern = Regex("for\\s+[`\"']?([^`\"'\\s]+)[`\"']?\\s+on\\s+port\\s+(\\d+)")
    private val algorithmPattern = Regex("verify\\s+([^\\s]+)\\s+host key")
    private val fingerprintPattern = Regex("fingerprint\\s+([^\\s]+)")

    fun parseUnknownHostKey(
        throwable: Throwable,
        fallbackHost: String,
        fallbackPort: Int
    ): SftpUnknownHostKeyException? {
        return throwable.causeSequence()
            .firstNotNullOfOrNull { cause ->
                parseUnknownHostKeyMessage(cause.message.orEmpty(), fallbackHost, fallbackPort)
            }
    }

    private fun parseUnknownHostKeyMessage(
        message: String,
        fallbackHost: String,
        fallbackPort: Int
    ): SftpUnknownHostKeyException? {
        if (!message.contains("[HOST_KEY_NOT_VERIFIABLE]")) {
            return null
        }
        val algorithm = algorithmPattern.find(message)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
            .unwrapped()
            .ifBlank { "unknown" }
        val fingerprint = fingerprintPattern.find(message)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
            .unwrapped()
            .ifBlank { "unknown" }
        val hostPortMatch = hostPortPattern.find(message)
        val host = hostPortMatch?.groupValues?.getOrNull(1)
            ?.unwrapped()
            .orEmpty()
            .ifBlank { fallbackHost }
        val port = hostPortMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: fallbackPort
        return SftpUnknownHostKeyException(
            host = host,
            port = port,
            algorithm = algorithm,
            presentedSha256Fingerprint = fingerprint,
            presentedEncodedKeyBase64 = ""
        )
    }

    private fun Throwable.causeSequence(): Sequence<Throwable> = sequence {
        var current: Throwable? = this@causeSequence
        while (current != null) {
            yield(current)
            current = current.cause
        }
    }

    private fun String.unwrapped(): String = trim().trim('`', '"', '\'')
}
