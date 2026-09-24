package com.wisso.wizefiles.storage

import java.net.InetAddress

data class LanSmbServer(
    val host: String,
    val address: InetAddress
) : Comparable<LanSmbServer> {
    override fun compareTo(other: LanSmbServer): Int {
        val addressOrder = address.hostAddress.orEmpty()
            .compareTo(other.address.hostAddress.orEmpty())
        return if (addressOrder != 0) addressOrder else host.compareTo(other.host)
    }
}
