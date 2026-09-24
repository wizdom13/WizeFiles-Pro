package com.wisso.wizefiles.provider.os.syscall

import com.wisso.wizefiles.provider.common.ByteString

class StructGroup(
    val gr_name: ByteString?,
    val gr_passwd: ByteString?,
    val gr_gid: Int,
    val gr_mem: Array<ByteString>?
)
