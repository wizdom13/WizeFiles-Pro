package com.wisso.wizefiles.provider.sftp

import java.nio.file.Path
import com.wisso.wizefiles.provider.sftp.client.Authority

fun Authority.createSftpRootPath(): Path =
    SftpFileSystemProvider.getOrNewFileSystem(this).rootDirectory
