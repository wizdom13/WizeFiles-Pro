// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

class ApkSigningKeyStoreService {
    fun aliases(
        keyStoreFile: File,
        format: ApkKeyStoreFormat,
        secrets: ApkSigningSecrets
    ): List<ApkSigningKeyAlias> {
        val keyStore = loadKeyStore(keyStoreFile, format, secrets)
        return keyStore.aliases().toList().mapNotNull { alias ->
            if (!keyStore.isKeyEntry(alias)) return@mapNotNull null
            val certificate = keyStore.getCertificate(alias) as? X509Certificate
                ?: return@mapNotNull null
            ApkSigningKeyAlias(
                alias = alias,
                certificateSha256 = sha256(certificate.encoded),
                subject = certificate.subjectX500Principal.name,
                notAfterMillis = certificate.notAfter.time
            )
        }.sortedBy { it.alias.lowercase(Locale.ROOT) }
    }

    fun load(
        keyStoreFile: File,
        format: ApkKeyStoreFormat,
        requestedAlias: String,
        secrets: ApkSigningSecrets
    ): ApkSigningKeyMaterial {
        val keyStore = loadKeyStore(keyStoreFile, format, secrets)
        val keyAliases = keyStore.aliases().toList().filter(keyStore::isKeyEntry)
        val alias = requestedAlias.takeIf(String::isNotBlank) ?: when (keyAliases.size) {
            1 -> keyAliases.single()
            0 -> throw IllegalArgumentException("The key store has no private-key entries")
            else -> throw IllegalArgumentException("Select a key alias")
        }
        require(alias in keyAliases) { "The selected key alias does not exist" }
        val keyPassword = secrets.keyPasswordCopy()
        return try {
            val privateKey = keyStore.getKey(alias, keyPassword) as? PrivateKey
                ?: throw IllegalArgumentException("The selected alias is not a private key")
            val certificates = keyStore.getCertificateChain(alias)
                ?.map {
                    it as? X509Certificate
                        ?: throw IllegalArgumentException("The certificate chain is not X.509")
                }
                .orEmpty()
            ApkSigningKeyMaterial(privateKey, certificates)
        } finally {
            keyPassword.fill('\u0000')
        }
    }

    fun generatePkcs12(
        output: File,
        request: ApkSigningKeyGenerationRequest,
        secrets: ApkSigningSecrets
    ): ApkSigningKeyAlias {
        require(!output.exists()) { "Key store output already exists" }
        require(output.parentFile?.isDirectory == true) { "Key store directory does not exist" }
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(request.rsaKeySize, SecureRandom())
        }.generateKeyPair()
        val now = Instant.now()
        val subject = X500Name(request.subjectDistinguishedName)
        val certificateBuilder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(160, SecureRandom()).abs().max(BigInteger.ONE),
            Date.from(now.minus(1, ChronoUnit.DAYS)),
            Date.from(now.plus(request.validityYears.toLong() * 366, ChronoUnit.DAYS)),
            subject,
            keyPair.public
        )
        val provider = BouncyCastleProvider()
        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(provider)
            .build(keyPair.private)
        val certificate = JcaX509CertificateConverter()
            .setProvider(provider)
            .getCertificate(certificateBuilder.build(signer))
        certificate.verify(keyPair.public)

        val storePassword = secrets.storePasswordCopy()
        val keyPassword = secrets.keyPasswordCopy()
        try {
            KeyStore.getInstance("PKCS12").apply {
                load(null, storePassword)
                setKeyEntry(
                    request.alias,
                    keyPair.private,
                    keyPassword,
                    arrayOf(certificate)
                )
                output.outputStream().buffered().use { store(it, storePassword) }
            }
        } catch (exception: Exception) {
            output.delete()
            throw exception
        } finally {
            storePassword.fill('\u0000')
            keyPassword.fill('\u0000')
        }
        return ApkSigningKeyAlias(
            request.alias,
            sha256(certificate.encoded),
            certificate.subjectX500Principal.name,
            certificate.notAfter.time
        )
    }

    private fun loadKeyStore(
        file: File,
        format: ApkKeyStoreFormat,
        secrets: ApkSigningSecrets
    ): KeyStore {
        require(file.isFile && file.canRead()) { "Key store is not readable" }
        val storePassword = secrets.storePasswordCopy()
        return try {
            val keyStore = when (format) {
                ApkKeyStoreFormat.PKCS12 -> KeyStore.getInstance("PKCS12")
                ApkKeyStoreFormat.JKS -> KeyStore.getInstance("JKS")
                ApkKeyStoreFormat.BKS -> KeyStore.getInstance("BKS", BouncyCastleProvider())
            }
            file.inputStream().buffered().use { keyStore.load(it, storePassword) }
            keyStore
        } finally {
            storePassword.fill('\u0000')
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            String.format(Locale.ROOT, "%02X", it.toInt() and 0xFF)
        }
}
