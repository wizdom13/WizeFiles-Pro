package com.wisso.wizefiles.provider.ftp.client

interface Authenticator {
    fun getPassword(authority: Authority): String?
}
