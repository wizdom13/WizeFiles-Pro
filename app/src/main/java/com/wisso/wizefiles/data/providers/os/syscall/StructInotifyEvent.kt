package com.wisso.wizefiles.provider.os.syscall

import com.wisso.wizefiles.provider.common.ByteString

class StructInotifyEvent(
    val wd: Int,
    val mask: Int, /* uint32_t */
    val cookie: Int, /* uint32_t */
    val name: ByteString?
)
