package com.wisso.wizefiles.provider.sftp.client

interface Authenticator {
    fun getAuthentication(authority: Authority): Authentication?
}
