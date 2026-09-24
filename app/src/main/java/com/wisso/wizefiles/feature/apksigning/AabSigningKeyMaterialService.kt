// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import java.io.File
import java.io.StringReader
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Locale
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEInputDecryptorProviderBuilder

class AabSigningKeyMaterialService(
    private val keyStoreService: ApkSigningKeyStoreService = ApkSigningKeyStoreService()
) {
    fun load(
        keyFile: File,
        certificateFile: File?,
        keySource: AabSigningKeySource,
        keyStoreFormat: ApkKeyStoreFormat,
        keyAlias: String,
        secrets: ApkSigningSecrets
    ): ApkSigningKeyMaterial = when (keySource) {
        AabSigningKeySource.KEY_STORE -> keyStoreService.load(
            keyFile,
            keyStoreFormat,
            keyAlias,
            secrets
        )
        AabSigningKeySource.PKCS8_CERTIFICATE -> loadPkcs8(
            keyFile,
            requireNotNull(certificateFile) { "An X.509 certificate file is required" },
            secrets
        )
    }

    private fun loadPkcs8(
        privateKeyFile: File,
        certificateFile: File,
        secrets: ApkSigningSecrets
    ): ApkSigningKeyMaterial {
        require(privateKeyFile.isFile && privateKeyFile.canRead()) {
            "PKCS#8 private key is not readable"
        }
        require(certificateFile.isFile && certificateFile.canRead()) {
            "X.509 certificate is not readable"
        }
        val password = secrets.storePasswordCopy()
        return try {
            val privateKey = parsePrivateKey(privateKeyFile.readBytes(), password)
            val certificates = certificateFile.inputStream().buffered().use { input ->
                CertificateFactory.getInstance("X.509")
                    .generateCertificates(input)
                    .map {
                        it as? X509Certificate
                            ?: throw IllegalArgumentException("Certificate chain is not X.509")
                    }
            }
            require(certificates.isNotEmpty()) { "Certificate file has no X.509 certificates" }
            requireKeyMatchesCertificate(privateKey, certificates.first())
            ApkSigningKeyMaterial(privateKey, certificates)
        } finally {
            password.fill('\u0000')
        }
    }

    private fun parsePrivateKey(bytes: ByteArray, password: CharArray): PrivateKey {
        val provider = BouncyCastleProvider()
        val parsed = if (String(bytes, Charsets.US_ASCII).contains("-----BEGIN")) {
            PEMParser(StringReader(String(bytes, Charsets.US_ASCII))).use { parser ->
                parser.readObject()
            }
        } else {
            runCatching { PrivateKeyInfo.getInstance(bytes) }.getOrElse {
                runCatching { PKCS8EncryptedPrivateKeyInfo(bytes) }.getOrElse { cause ->
                    throw IllegalArgumentException("Private key is not PKCS#8", cause)
                }
            }
        }
        val privateKeyInfo = when (parsed) {
            is PrivateKeyInfo -> parsed
            is PKCS8EncryptedPrivateKeyInfo -> {
                val decryptor = JcePKCSPBEInputDecryptorProviderBuilder()
                    .setProvider(provider)
                    .build(password)
                runCatching { parsed.decryptPrivateKeyInfo(decryptor) }.getOrElse { cause ->
                    throw IllegalArgumentException(
                        "Encrypted PKCS#8 key could not be unlocked",
                        cause
                    )
                }
            }
            else -> throw IllegalArgumentException("Private key is not PKCS#8")
        }
        return JcaPEMKeyConverter().setProvider(provider).getPrivateKey(privateKeyInfo)
    }

    private fun requireKeyMatchesCertificate(
        privateKey: PrivateKey,
        certificate: X509Certificate
    ) {
        val algorithm = when (privateKey.algorithm.uppercase(Locale.ROOT)) {
            "RSA" -> "SHA256withRSA"
            "EC", "ECDSA" -> "SHA256withECDSA"
            "DSA" -> "SHA256withDSA"
            else -> throw IllegalArgumentException("Unsupported private-key algorithm")
        }
        val challenge = MessageDigest.getInstance("SHA-256")
            .digest("WizeFiles AAB key match".toByteArray())
        val signature = Signature.getInstance(algorithm).run {
            initSign(privateKey)
            update(challenge)
            sign()
        }
        val matches = Signature.getInstance(algorithm).run {
            initVerify(certificate.publicKey)
            update(challenge)
            verify(signature)
        }
        require(matches) { "Private key does not match the selected certificate" }
    }
}
