// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** Ephemeral keys stay in memory. Peer identity is established by the session-bound QR proof. */
internal class NearbyTlsIdentity private constructor(val context: SSLContext, val certificate: X509Certificate) {
    companion object {
        fun create(): NearbyTlsIdentity {
            val random = SecureRandom()
            val keys = KeyPairGenerator.getInstance("EC").apply {
                initialize(ECGenParameterSpec("secp256r1"), random)
            }.generateKeyPair()
            val provider = BouncyCastleProvider()
            val name = X500Name("CN=WizeFiles temporary transfer")
            val now = System.currentTimeMillis()
            val certificate = JcaX509CertificateConverter().setProvider(provider).getCertificate(
                JcaX509v3CertificateBuilder(
                    name, BigInteger(160, random), Date(now - 86_400_000), Date(now + 86_400_000), name, keys.public
                ).build(JcaContentSignerBuilder("SHA256withECDSA").setProvider(provider).build(keys.private))
            )
            val password = CharArray(32) { random.nextInt(94).plus(33).toChar() }
            val store = KeyStore.getInstance("PKCS12").apply {
                load(null, password)
                setKeyEntry("nearby", keys.private, password, arrayOf(certificate))
            }
            val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(store, password)
            }
            password.fill('\u0000')
            return NearbyTlsIdentity(SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, random) }, certificate)
        }

        /** This context ONLY permits pairing; NearbyLanConnection gates all payloads on QR approval. */
        fun pairingContext(): SSLContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                    throw java.security.cert.CertificateException("Client certificates are not used")
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    if (chain.size != 1 || chain[0].publicKey.algorithm != "EC") {
                        throw java.security.cert.CertificateException("Expected an ephemeral EC certificate")
                    }
                    // No wall-clock dependency between offline phones: possession and identity are
                    // checked by TLS and QR, not a public CA or this certificate's date range.
                    chain[0].verify(chain[0].publicKey)
                }
            }), SecureRandom())
        }

        fun configure(socket: SSLSocket) {
            socket.enabledProtocols = socket.supportedProtocols.filter { it == "TLSv1.3" || it == "TLSv1.2" }.toTypedArray()
            socket.enabledCipherSuites = socket.supportedCipherSuites.filter {
                it.startsWith("TLS_AES_") || it == "TLS_CHACHA20_POLY1305_SHA256" ||
                    (it.startsWith("TLS_ECDHE_ECDSA_WITH_") && ("GCM" in it || "CHACHA20" in it))
            }.toTypedArray()
            socket.soTimeout = 15_000
            socket.tcpNoDelay = true
        }

        fun proof(certificate: ByteArray, clientNonce: ByteArray, serverNonce: ByteArray): ByteArray {
            require(clientNonce.size == 32 && serverNonce.size == 32)
            return MessageDigest.getInstance("SHA-256").run {
                update("WizeFiles LAN TLS pairing v2\u0000".toByteArray(Charsets.UTF_8))
                update(MessageDigest.getInstance("SHA-256").digest(certificate))
                update(clientNonce)
                digest(serverNonce)
            }
        }
    }
}
