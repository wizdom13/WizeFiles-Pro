package com.wisso.wizefiles.provider.common

import java.net.URI
import java.net.URISyntaxException

data class UriAuthority(
    val userInfo: String?,
    val host: String,
    val port: Int?
) {
    fun encode(): String = createEncodingUri().rawAuthority.orEmpty()

    override fun toString(): String {
        val credentials = userInfo?.let { it + '@' }.orEmpty()
        val endpointPort = port?.let { ":" + it }.orEmpty()
        return credentials + host + endpointPort
    }

    private fun createEncodingUri(): URI =
        try {
            URI(null, userInfo, host, port ?: NO_PORT, "/", null, null)
        } catch (exception: URISyntaxException) {
            throw IllegalArgumentException("Invalid URI authority", exception)
        }

    companion object {
        val EMPTY = UriAuthority(userInfo = null, host = "", port = null)

        private const val NO_PORT = -1
    }
}
