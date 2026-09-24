package com.wisso.wizefiles.feature.share

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.security.auth.x500.X500Principal

internal data class LocalTlsIdentity(val context:SSLContext,val fingerprint:String)

internal object LocalTls {
    fun identity(alias:String):LocalTlsIdentity {
        val keyStore=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        if(!keyStore.containsAlias(alias)){
            val now=System.currentTimeMillis();val spec=KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA512).setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1).setCertificateSubject(X500Principal("CN=WizeFiles Local Sharing"))
                .setCertificateSerialNumber(BigInteger.valueOf(now)).setCertificateNotBefore(Date(now-24*60*60_000)).setCertificateNotAfter(Date(now+10L*365*24*60*60_000)).build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore").apply{initialize(spec);generateKeyPair()}
        }
        val factory=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply{init(keyStore,null)}
        val context=SSLContext.getInstance("TLS").apply{init(factory.keyManagers,null,java.security.SecureRandom())}
        val digest=MessageDigest.getInstance("SHA-256").digest(keyStore.getCertificate(alias).encoded).joinToString(":"){"%02X".format(it)}
        return LocalTlsIdentity(context,digest)
    }
}
