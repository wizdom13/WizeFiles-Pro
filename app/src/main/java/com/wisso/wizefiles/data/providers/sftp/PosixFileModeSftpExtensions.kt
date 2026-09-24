package com.wisso.wizefiles.provider.sftp

import com.wisso.wizefiles.provider.common.PosixFileModeBit
import com.wisso.wizefiles.provider.common.toInt
import net.schmizz.sshj.sftp.FileAttributes

fun Set<PosixFileModeBit>.toSftpAttributes(): FileAttributes =
    FileAttributes.Builder().withPermissions(toInt()).build()
