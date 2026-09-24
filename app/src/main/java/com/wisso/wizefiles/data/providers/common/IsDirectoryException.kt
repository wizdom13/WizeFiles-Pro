package com.wisso.wizefiles.provider.common

import java.nio.file.FileSystemException

class IsDirectoryException : FileSystemException {
    constructor(file: String?) : super(file)

    constructor(file: String?, other: String?, reason: String?) : super(file, other, reason)
}
