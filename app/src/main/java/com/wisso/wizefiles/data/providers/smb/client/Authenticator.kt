package com.wisso.wizefiles.provider.smb.client

interface Authenticator {
    fun getPassword(authority: Authority): String?
}
