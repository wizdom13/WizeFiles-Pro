package com.wisso.wizefiles.provider.common

import java.nio.file.FileSystemException

class InvalidFileNameException : FileSystemException {
    constructor(file: String?) : super(file)

    constructor(file: String?, other: String?, reason: String?) : super(file, other, reason)
}
