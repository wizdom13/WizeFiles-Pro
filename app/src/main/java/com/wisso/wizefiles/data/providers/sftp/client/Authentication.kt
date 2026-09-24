// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.sftp.client

import android.os.Parcelable
import com.wisso.wizefiles.security.NullableSecretStringParceler
import com.wisso.wizefiles.security.SecretStringParceler
import java.io.IOException
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.common.Factory
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.keyprovider.KeyProviderUtil
import net.schmizz.sshj.userauth.method.AuthMethod
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.AuthPublickey
import net.schmizz.sshj.userauth.password.PasswordUtils

sealed class Authentication : Parcelable {
    abstract fun toAuthMethod(): AuthMethod
}

@Parcelize
data class PasswordAuthentication(
    val password: @WriteWith<SecretStringParceler> String
) : Authentication() {
    override fun toAuthMethod(): AuthMethod {
        val finder = PasswordUtils.createOneOff(password.toCharArray())
        return AuthPassword(finder)
    }
}

@Parcelize
data class PublicKeyAuthentication(
    val privateKey: @WriteWith<SecretStringParceler> String,
    val privateKeyPassword: @WriteWith<NullableSecretStringParceler> String?
) : Authentication() {
    override fun toAuthMethod(): AuthMethod =
        AuthPublickey(KeyMaterialLoader.load(privateKey, privateKeyPassword))

    companion object {
        fun validate(privateKey: String, privateKeyPassword: String?): IOException? =
            try {
                KeyMaterialLoader.load(privateKey, privateKeyPassword).private
                null
            } catch (failure: IOException) {
                failure
            }
    }
}

private object KeyMaterialLoader {
    private val factories by lazy(LazyThreadSafetyMode.PUBLICATION) {
        DefaultConfig().fileKeyProviderFactories.toList()
    }

    @Throws(IOException::class)
    fun load(encodedKey: String, passphrase: String?): KeyProvider {
        val format = KeyProviderUtil.detectKeyFileFormat(encodedKey, false)
        val provider = Factory.Named.Util.create(factories, format.toString())
            ?: throw IOException("SSH key format has no provider: " + format)
        val passwordFinder = passphrase?.toCharArray()?.let { chars ->
            PasswordUtils.createOneOff(chars)
        }
        provider.init(encodedKey, null, passwordFinder)
        return provider
    }
}
