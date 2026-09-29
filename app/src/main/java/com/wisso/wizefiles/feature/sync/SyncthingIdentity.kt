// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import java.io.StringReader
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter

internal data class SyncthingIdentity(val certificate: ByteArray, val privateKey: ByteArray) {
    fun validate(): String {
        require(certificate.size in 1..65536 && privateKey.size in 1..65536)
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(certificate.inputStream())
        val provider = BouncyCastleProvider()
        val converter = JcaPEMKeyConverter().setProvider(provider)
        val key = PEMParser(StringReader(privateKey.toString(Charsets.US_ASCII))).use { parser ->
            val parsed = parser.readObject()
            require(parser.readObject() == null) { "Multiple identity keys are not supported" }
            when (parsed) {
                is PEMKeyPair -> converter.getPrivateKey(parsed.privateKeyInfo)
                is PrivateKeyInfo -> converter.getPrivateKey(parsed)
                else -> error("Unsupported or encrypted identity key")
            }
        }
        val algorithm = when (cert.publicKey.algorithm.uppercase()) {
            "EC", "ECDSA" -> "SHA256withECDSA"
            "RSA" -> "SHA256withRSA"
            "ED25519", "EDDSA" -> "Ed25519"
            else -> error("Unsupported Syncthing identity algorithm")
        }
        val challenge = ByteArray(32).also(SecureRandom()::nextBytes)
        val signature = Signature.getInstance(algorithm, provider).apply {
            initSign(key); update(challenge)
        }.sign()
        require(Signature.getInstance(algorithm, provider).apply {
            initVerify(cert.publicKey); update(challenge)
        }.verify(signature)) { "The identity certificate and key do not match" }
        return deviceId(cert.encoded)
    }
    companion object {
        // Syncthing IDs: SHA-256 certificate digest, RFC 4648 base32, four mod-32 check digits.
        fun deviceId(certificateDer: ByteArray): String {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            val digest = MessageDigest.getInstance("SHA-256").digest(certificateDer)
            var bits = 0
            var buffer = 0
            val encoded = buildString {
                digest.forEach { byte ->
                    buffer = (buffer shl 8) or (byte.toInt() and 255)
                    bits += 8
                    while (bits >= 5) { bits -= 5; append(alphabet[(buffer ushr bits) and 31]) }
                }
                if (bits > 0) append(alphabet[(buffer shl (5 - bits)) and 31])
            }
            val checked = encoded.chunked(13).joinToString("") { part ->
                val sum = part.mapIndexed { index, char ->
                    val product = alphabet.indexOf(char) * (1 + index % 2)
                    product / 32 + product % 32
                }.sum()
                part + alphabet[(32 - sum % 32) % 32]
            }
            return checked.chunked(7).joinToString("-")
        }
    }
}
