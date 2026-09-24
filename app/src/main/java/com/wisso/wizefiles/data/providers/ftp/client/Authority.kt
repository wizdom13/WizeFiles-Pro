package com.wisso.wizefiles.provider.ftp.client

import android.os.Parcelable
import com.wisso.wizefiles.provider.common.UriAuthority
import java.nio.charset.StandardCharsets
import kotlinx.parcelize.Parcelize

@Parcelize
data class Authority(
    val protocol: Protocol,
    val host: String,
    val port: Int,
    val username: String,
    val mode: Mode,
    val encoding: String
) : Parcelable {
    fun toUriAuthority(): UriAuthority =
        UriAuthority(
            userInfo = username.ifEmpty { null },
            host = host,
            port = port.takeUnless { it == protocol.defaultPort }
        )

    override fun toString(): String = toUriAuthority().toString()

    companion object {
        const val ANONYMOUS_USERNAME = "anonymous"
        const val ANONYMOUS_PASSWORD = "guest"
        val DEFAULT_MODE: Mode = Mode.PASSIVE
        val DEFAULT_ENCODING: String = StandardCharsets.UTF_8.name()
    }
}
