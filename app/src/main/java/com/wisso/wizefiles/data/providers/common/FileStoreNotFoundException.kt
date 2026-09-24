package com.wisso.wizefiles.provider.common

import java.nio.file.FileSystemException

class FileStoreNotFoundException(file: String?) : FileSystemException(file)
