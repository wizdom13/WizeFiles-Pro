package com.wisso.wizefiles.security

interface SecretStore {
    fun getSecret(key: String): String?

    fun putSecret(key: String, value: String?)

    fun removeSecret(key: String)
}
