// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder

/** Android-compatible AAB upload-key signer. AABs use JAR signing, not APK v1-v4. */
class JarAabSigningBackend(
    private val inspector: AndroidPackageContainerInspector = AndroidPackageContainerInspector()
) : AabSigningBackend {
    override fun sign(request: AabSigningRequest): AabSigningResult {
        validateSigningRequest(request)
        inspector.inspect(request.inputAab, AndroidPackageContainerHint.AAB)
        try {
            val manifest = buildManifest(request.inputAab)
            val signatureFileBytes = buildSignatureFile(manifest)
            val signatureBlock = buildSignatureBlock(signatureFileBytes, request.keyMaterial)
            writeSignedBundle(
                request.inputAab,
                request.outputAab,
                manifest.bytes,
                signatureFileBytes,
                signatureBlock,
                signatureBlockExtension(request.keyMaterial.privateKey.algorithm)
            )
            val verification = verify(AabVerificationRequest(request.outputAab))
            val expectedCertificate = sha256Hex(request.keyMaterial.certificates.first().encoded)
            if (!verification.verified ||
                verification.signerCertificates.none { it.sha256 == expectedCertificate }) {
                throw AabSigningBackendException(
                    if (verification.verified) {
                        "Signed AAB verification found a different signer certificate"
                    } else {
                        "Signed AAB verification failed: ${verification.errors.joinToString()}"
                    }
                )
            }
            return AabSigningResult(request.outputAab, verification)
        } catch (exception: Exception) {
            request.outputAab.delete()
            if (exception is AabSigningBackendException) throw exception
            throw AabSigningBackendException("Unable to sign AAB", exception)
        }
    }

    override fun verify(request: AabVerificationRequest): AabVerificationReport {
        requireReadableFile(request.aab, "AAB")
        return try {
            inspector.inspect(request.aab, AndroidPackageContainerHint.AAB)
            verifySignedBundle(request.aab)
        } catch (exception: Exception) {
            if (exception is AabSigningBackendException) throw exception
            throw AabSigningBackendException("Unable to verify AAB upload signature", exception)
        }
    }

    private fun buildManifest(input: File): ManifestPayload {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes[Attributes.Name("Created-By")] = CREATED_BY
        }
        ZipFile(input).use { zip ->
            zip.entries().asSequence()
                .filterNot(ZipEntry::isDirectory)
                .filterNot { isSignatureEntry(it.name) }
                .forEach { entry ->
                    require('\r' !in entry.name && '\n' !in entry.name) {
                        "AAB entry path contains a manifest control character"
                    }
                    val digest = zip.getInputStream(entry).buffered().use(::sha256)
                    manifest.entries[entry.name] = Attributes().apply {
                        putValue(DIGEST_ATTRIBUTE, Base64.getEncoder().encodeToString(digest))
                    }
                }
        }
        val bytes = ByteArrayOutputStream().use { output ->
            manifest.write(output)
            output.toByteArray()
        }
        return ManifestPayload(bytes, manifestSectionBytes(bytes))
    }

    private fun buildSignatureFile(manifest: ManifestPayload): ByteArray {
        val signatureFile = Manifest().apply {
            mainAttributes[Attributes.Name.SIGNATURE_VERSION] = "1.0"
            mainAttributes[Attributes.Name("Created-By")] = CREATED_BY
            mainAttributes[Attributes.Name(MANIFEST_DIGEST_ATTRIBUTE)] =
                Base64.getEncoder().encodeToString(sha256Digest(manifest.bytes))
            manifest.sections.forEach { (name, section) ->
                entries[name] = Attributes().apply {
                    putValue(
                        DIGEST_ATTRIBUTE,
                        Base64.getEncoder().encodeToString(sha256Digest(section))
                    )
                }
            }
        }
        return ByteArrayOutputStream().use { output ->
            signatureFile.write(output)
            output.toByteArray()
        }
    }

    private fun buildSignatureBlock(
        signatureFileBytes: ByteArray,
        keyMaterial: ApkSigningKeyMaterial
    ): ByteArray {
        val provider = BouncyCastleProvider()
        val signatureAlgorithm = when (keyMaterial.privateKey.algorithm.uppercase(Locale.ROOT)) {
            "RSA" -> "SHA256withRSA"
            "EC", "ECDSA" -> "SHA256withECDSA"
            "DSA" -> "SHA256withDSA"
            else -> throw AabSigningBackendException("Unsupported AAB signing key algorithm")
        }
        val contentSigner = JcaContentSignerBuilder(signatureAlgorithm)
            .setProvider(provider)
            .build(keyMaterial.privateKey)
        val digestProvider = JcaDigestCalculatorProviderBuilder()
            .setProvider(provider)
            .build()
        val generator = CMSSignedDataGenerator().apply {
            addSignerInfoGenerator(
                JcaSignerInfoGeneratorBuilder(digestProvider)
                    .build(contentSigner, keyMaterial.certificates.first())
            )
            addCertificates(JcaCertStore(keyMaterial.certificates))
        }
        return generator.generate(CMSProcessableByteArray(signatureFileBytes), false).encoded
    }

    private fun writeSignedBundle(
        input: File,
        output: File,
        manifestBytes: ByteArray,
        signatureFileBytes: ByteArray,
        signatureBlock: ByteArray,
        blockExtension: String
    ) {
        ZipFile(input).use { zip ->
            ZipOutputStream(FileOutputStream(output).buffered()).use { target ->
                target.setLevel(9)
                target.writeEntry(MANIFEST_PATH, manifestBytes)
                target.writeEntry(SIGNATURE_FILE_PATH, signatureFileBytes)
                target.writeEntry("$SIGNATURE_BASE.$blockExtension", signatureBlock)
                zip.entries().asSequence()
                    .filterNot { isSignatureEntry(it.name) }
                    .forEach { original ->
                        val replacement = ZipEntry(original.name).apply {
                            method = original.method
                            if (original.time >= 0) time = original.time
                            comment = original.comment
                            extra = original.extra
                            if (original.method == ZipEntry.STORED) {
                                size = original.size
                                compressedSize = original.size
                                crc = original.crc
                            }
                        }
                        target.putNextEntry(replacement)
                        if (!original.isDirectory) {
                            zip.getInputStream(original).buffered().use { it.copyTo(target) }
                        }
                        target.closeEntry()
                    }
            }
        }
    }

    private fun verifySignedBundle(file: File): AabVerificationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val certificates = linkedMapOf<String, AabSignerCertificate>()
        var signedEntryCount = 0
        ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence().toList()
            val manifestEntry = entries.singleOrNull { it.name.equals(MANIFEST_PATH, true) }
            if (manifestEntry == null) {
                return AabVerificationReport(false, emptyList(), 0,
                    listOf("META-INF/MANIFEST.MF is missing"), emptyList())
            }
            val manifestBytes = zip.getInputStream(manifestEntry).buffered().use {
                it.readBoundedSignatureMetadata(MANIFEST_PATH)
            }
            val manifest = runCatching { Manifest(manifestBytes.inputStream()) }.getOrElse {
                throw AabSigningBackendException("AAB manifest signature metadata is invalid", it)
            }
            val manifestSections = manifestSectionBytes(manifestBytes)
            val contentEntries = entries.filterNot(ZipEntry::isDirectory)
                .filterNot { isSignatureEntry(it.name) }
            val contentNames = contentEntries.mapTo(linkedSetOf()) { it.name }
            contentEntries.forEach { entry ->
                val expectedText = manifest.entries[entry.name]?.getValue(DIGEST_ATTRIBUTE)
                if (expectedText == null) {
                    errors += "${entry.name}: missing SHA-256 manifest digest"
                    return@forEach
                }
                val expected = runCatching { Base64.getDecoder().decode(expectedText) }.getOrNull()
                val actual = zip.getInputStream(entry).buffered().use(::sha256)
                if (expected == null || !MessageDigest.isEqual(expected, actual)) {
                    errors += "${entry.name}: content digest does not match"
                } else {
                    signedEntryCount++
                }
            }
            manifest.entries.keys.filterNot(contentNames::contains).forEach {
                errors += "$it: signature metadata references a missing entry"
            }

            val signatureFiles = entries.filter {
                !it.isDirectory && it.name.uppercase(Locale.ROOT).let { name ->
                    name.startsWith("META-INF/") && name.endsWith(".SF") &&
                        '/' !in name.removePrefix("META-INF/")
                }
            }
            if (signatureFiles.isEmpty()) errors += "AAB has no JAR signature file"
            signatureFiles.forEach signatureLoop@ { signatureEntry ->
                val base = signatureEntry.name.substringBeforeLast('.')
                val block = entries.singleOrNull { candidate ->
                    candidate.name.substringBeforeLast('.').equals(base, true) &&
                        candidate.name.substringAfterLast('.').uppercase(Locale.ROOT) in
                        SIGNATURE_BLOCK_EXTENSIONS
                }
                if (block == null) {
                    errors += "${signatureEntry.name}: signature block is missing"
                    return@signatureLoop
                }
                val signatureFileBytes = zip.getInputStream(signatureEntry).buffered()
                    .use { it.readBoundedSignatureMetadata(signatureEntry.name) }
                val signatureFile = runCatching {
                    Manifest(signatureFileBytes.inputStream())
                }.getOrElse {
                    errors += "${signatureEntry.name}: signature file is invalid"
                    return@signatureLoop
                }
                val manifestDigestText = signatureFile.mainAttributes
                    .getValue(MANIFEST_DIGEST_ATTRIBUTE)
                val manifestDigest = runCatching {
                    Base64.getDecoder().decode(manifestDigestText ?: "")
                }.getOrNull()
                if (manifestDigest == null || !MessageDigest.isEqual(
                        manifestDigest,
                        sha256Digest(manifestBytes)
                    )) {
                    errors += "${signatureEntry.name}: manifest digest does not match"
                    return@signatureLoop
                }
                contentEntries.forEach contentLoop@ { contentEntry ->
                    val section = manifestSections[contentEntry.name]
                    val expectedText = signatureFile.entries[contentEntry.name]
                        ?.getValue(DIGEST_ATTRIBUTE)
                    val expected = runCatching {
                        Base64.getDecoder().decode(expectedText ?: "")
                    }.getOrNull()
                    if (section == null || expected == null || !MessageDigest.isEqual(
                            expected,
                            sha256Digest(section)
                        )) {
                        errors += "${signatureEntry.name}: ${contentEntry.name} manifest section is not signed"
                        return@contentLoop
                    }
                }
                val blockBytes = zip.getInputStream(block).buffered().use {
                    it.readBoundedSignatureMetadata(block.name)
                }
                val cms = runCatching {
                    CMSSignedData(CMSProcessableByteArray(signatureFileBytes), blockBytes)
                }.getOrElse {
                    errors += "${block.name}: CMS signature block is invalid"
                    return@signatureLoop
                }
                val provider = BouncyCastleProvider()
                var validSignerCount = 0
                cms.signerInfos.signers.forEach signerLoop@ { signer ->
                    val matches = cms.certificates.getMatches(null)
                        .filter { signer.sid.match(it) }
                    if (matches.isEmpty()) {
                        errors += "${block.name}: signer certificate is missing"
                        return@signerLoop
                    }
                    val holder = matches.first() as X509CertificateHolder
                    val valid = runCatching {
                        signer.verify(
                            JcaSimpleSignerInfoVerifierBuilder()
                                .setProvider(provider)
                                .build(holder)
                        )
                    }.getOrDefault(false)
                    if (!valid) {
                        errors += "${block.name}: CMS signature is not valid"
                        return@signerLoop
                    }
                    validSignerCount++
                    val certificate = JcaX509CertificateConverter()
                        .setProvider(provider)
                        .getCertificate(holder)
                    val fingerprint = sha256Hex(certificate.encoded)
                    certificates[fingerprint] = certificate.toReport(fingerprint)
                    if (!certificate.isValidAt(Date())) {
                        warnings += "$fingerprint: signer certificate is expired or not yet valid"
                    }
                }
                if (validSignerCount == 0) errors += "${block.name}: no valid signer"
            }
        }
        if (errors.isEmpty()) verifyWithJarRuntime(file, errors)
        return AabVerificationReport(
            verified = errors.isEmpty() && certificates.isNotEmpty() && signedEntryCount > 0,
            signerCertificates = certificates.values.toList(),
            signedEntryCount = signedEntryCount,
            errors = errors.distinct(),
            warnings = warnings.distinct()
        )
    }

    private fun verifyWithJarRuntime(file: File, errors: MutableList<String>) {
        runCatching {
            JarFile(file, true).use { jar ->
                jar.entries().asSequence()
                    .filterNot(ZipEntry::isDirectory)
                    .filterNot { isSignatureEntry(it.name) }
                    .forEach { entry ->
                        jar.getInputStream(entry).buffered().use { it.copyTo(NULL_OUTPUT) }
                        if (entry.certificates.isNullOrEmpty()) {
                            errors += "${entry.name}: platform JAR verifier found no signer"
                        }
                    }
            }
        }.onFailure {
            errors += "Platform JAR verifier rejected the AAB signature"
        }
    }

    private fun validateSigningRequest(request: AabSigningRequest) {
        requireReadableFile(request.inputAab, "Input AAB")
        require(request.inputAab.canonicalFile != request.outputAab.canonicalFile) {
            "The original AAB cannot be overwritten"
        }
        require(!request.outputAab.exists()) { "Output AAB already exists" }
        require(request.outputAab.parentFile?.isDirectory == true) {
            "Output AAB directory does not exist"
        }
    }

    private fun requireReadableFile(file: File, label: String) {
        require(file.isFile && file.canRead()) { "$label is not a readable regular file" }
    }

    private fun signatureBlockExtension(algorithm: String): String =
        when (algorithm.uppercase(Locale.ROOT)) {
            "RSA" -> "RSA"
            "EC", "ECDSA" -> "EC"
            "DSA" -> "DSA"
            else -> throw AabSigningBackendException("Unsupported AAB signing key algorithm")
        }

    private fun manifestSectionBytes(bytes: ByteArray): Map<String, ByteArray> {
        val sections = mutableListOf<ByteArray>()
        var start = 0
        var index = 0
        while (index <= bytes.size - SECTION_SEPARATOR.size) {
            if (SECTION_SEPARATOR.indices.all { offset ->
                    bytes[index + offset] == SECTION_SEPARATOR[offset]
                }) {
                val end = index + SECTION_SEPARATOR.size
                sections += bytes.copyOfRange(start, end)
                start = end
                index = end
            } else {
                index++
            }
        }
        if (start != bytes.size || sections.isEmpty()) {
            throw AabSigningBackendException("AAB signature manifest has invalid line endings")
        }
        return sections.drop(1).associateBy { section ->
            val lines = String(section, Charsets.UTF_8).split("\r\n")
            require(lines.firstOrNull()?.startsWith("Name: ") == true) {
                "AAB signature manifest entry has no name"
            }
            buildString {
                append(lines.first().removePrefix("Name: "))
                lines.drop(1).takeWhile { it.startsWith(' ') }.forEach {
                    append(it.removePrefix(" "))
                }
            }
        }
    }

    private fun isSignatureEntry(name: String): Boolean {
        val upper = name.uppercase(Locale.ROOT)
        if (upper == MANIFEST_PATH) return true
        if (!upper.startsWith("META-INF/")) return false
        val leaf = upper.removePrefix("META-INF/")
        if ('/' in leaf) return false
        return leaf.endsWith(".SF") ||
            SIGNATURE_BLOCK_EXTENSIONS.any { leaf.endsWith(".$it") } ||
            leaf.startsWith("SIG-")
    }

    private fun sha256Digest(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun sha256(input: java.io.InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        sha256Digest(bytes).joinToString("") {
            String.format(Locale.ROOT, "%02X", it.toInt() and 0xFF)
        }

    private fun X509Certificate.toReport(fingerprint: String) = AabSignerCertificate(
        sha256 = fingerprint,
        subject = subjectX500Principal.name,
        issuer = issuerX500Principal.name,
        notBeforeMillis = notBefore.time,
        notAfterMillis = notAfter.time
    )

    private fun X509Certificate.isValidAt(date: Date): Boolean =
        runCatching { checkValidity(date) }.isSuccess

    private fun InputStream.readBoundedSignatureMetadata(name: String): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total = Math.addExact(total, count.toLong())
            if (total > MAX_SIGNATURE_METADATA_BYTES) {
                throw AabSigningBackendException(
                    "$name exceeds the allowed JAR signature metadata size"
                )
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun ZipOutputStream.writeEntry(name: String, content: ByteArray) {
        putNextEntry(ZipEntry(name).apply { time = 0L })
        write(content)
        closeEntry()
    }

    private companion object {
        const val CREATED_BY = "WizeFiles"
        const val MANIFEST_PATH = "META-INF/MANIFEST.MF"
        const val MAX_SIGNATURE_METADATA_BYTES = 4L * 1024 * 1024
        const val SIGNATURE_BASE = "META-INF/WIZEFILE"
        const val SIGNATURE_FILE_PATH = "$SIGNATURE_BASE.SF"
        const val DIGEST_ATTRIBUTE = "SHA-256-Digest"
        const val MANIFEST_DIGEST_ATTRIBUTE = "SHA-256-Digest-Manifest"
        val SIGNATURE_BLOCK_EXTENSIONS = setOf("RSA", "DSA", "EC")
        val SECTION_SEPARATOR = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(),
            '\r'.code.toByte(), '\n'.code.toByte())
        val NULL_OUTPUT = object : java.io.OutputStream() {
            override fun write(value: Int) = Unit
            override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
        }
    }

    private data class ManifestPayload(
        val bytes: ByteArray,
        val sections: Map<String, ByteArray>
    )
}
