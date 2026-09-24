package com.wisso.wizefiles.provider.common

import java.nio.file.FileSystem

interface ByteStringListPathCreator {
    fun getPath(first: ByteString, vararg more: ByteString): ByteStringListPath<*>
}

fun FileSystem.getPath(
    first: ByteString,
    vararg more: ByteString
): ByteStringListPath<*> {
    val creator = this as? ByteStringListPathCreator
        ?: throw UnsupportedOperationException(
            "File system does not accept byte-oriented paths: " + javaClass.name
        )
    return creator.getPath(first, *more)
}
