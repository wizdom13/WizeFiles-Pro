package com.wisso.wizefiles.provider.ftp

import java.nio.file.Path
import com.wisso.wizefiles.provider.ftp.client.Authority

fun Authority.createFtpRootPath(): Path =
    FtpFileSystemProvider.getOrNewFileSystem(this).rootDirectory
