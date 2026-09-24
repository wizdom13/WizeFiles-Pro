package com.wisso.wizefiles.provider.common

import java.nio.file.LinkOption

class LinkOptions(val noFollowLinks: Boolean) {
    fun toArray(): Array<LinkOption> =
        if (noFollowLinks) arrayOf(LinkOption.NOFOLLOW_LINKS) else emptyArray()
}

fun Array<out LinkOption>.toLinkOptions(): LinkOptions =
    LinkOptions(any { it === LinkOption.NOFOLLOW_LINKS })
