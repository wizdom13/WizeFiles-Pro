package com.wisso.wizefiles.provider.common

import java.nio.file.attribute.BasicFileAttributes

interface EncryptedFileAttributes {
    fun isEncrypted(): Boolean
}

fun BasicFileAttributes.isEncrypted(): Boolean =
    (this as? EncryptedFileAttributes)?.isEncrypted() ?: false
