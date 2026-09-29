// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import java.io.StringWriter
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.*
import org.junit.Test

internal fun testSyncthingIdentity(): SyncthingIdentity {
    val pair = KeyPairGenerator.getInstance("EC", BouncyCastleProvider())
        .apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    val name = X500Name("CN=Syncthing Test Identity")
    val cert = JcaX509v3CertificateBuilder(name, BigInteger.ONE, Date(0), Date(4_102_444_800_000L), name, pair.public)
        .build(JcaContentSignerBuilder("SHA256withECDSA").setProvider(BouncyCastleProvider()).build(pair.private))
    fun pem(value: Any) = StringWriter().also { writer -> JcaPEMWriter(writer).use { it.writeObject(value) } }
        .toString().toByteArray(Charsets.US_ASCII)
    return SyncthingIdentity(pem(cert), pem(pair))
}

class SyncthingIdentityTest {
    @Test fun matchingNativeStylePemIdentityIsAcceptedButMismatchedKeyIsRejected() {
        val first = testSyncthingIdentity()
        val second = testSyncthingIdentity()
        assertTrue(first.validate().matches(Regex("[A-Z2-7]{7}(-[A-Z2-7]{7}){7}")))
        assertNotEquals(first.validate(), second.validate())
        assertThrows(IllegalArgumentException::class.java) {
            SyncthingIdentity(first.certificate, second.privateKey).validate()
        }
    }
}
