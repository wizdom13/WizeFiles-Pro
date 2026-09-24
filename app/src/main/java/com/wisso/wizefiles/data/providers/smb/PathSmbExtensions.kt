package com.wisso.wizefiles.provider.smb

import java.nio.file.Path
import com.wisso.wizefiles.provider.smb.client.Authority

fun Authority.createSmbRootPath(): Path =
    SmbFileSystemProvider.getOrNewFileSystem(this).rootDirectory
