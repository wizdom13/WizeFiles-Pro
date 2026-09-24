// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.ebook

import java.io.File
import java.io.IOException

/** Read-only JNI bridge around libmobi. DRM and Print Replica conversion are intentionally absent. */
object MobiConverterNative {
    init {
        System.loadLibrary("mobi-jni")
    }

    @Throws(IOException::class)
    fun convertToBundle(source: File, outputDirectory: File) {
        require(source.isFile) { "MOBI source must be a regular staged file" }
        require(outputDirectory.isDirectory) { "MOBI output directory is unavailable" }
        when (convert(source.absolutePath, outputDirectory.absolutePath)) {
            STATUS_OK -> Unit
            STATUS_ENCRYPTED -> throw MobiConversionException(MobiFailure.ENCRYPTED)
            STATUS_PRINT_REPLICA -> throw MobiConversionException(MobiFailure.PRINT_REPLICA)
            STATUS_LIMIT -> throw MobiConversionException(MobiFailure.LIMIT_EXCEEDED)
            STATUS_INVALID -> throw MobiConversionException(MobiFailure.INVALID_DOCUMENT)
            else -> throw MobiConversionException(MobiFailure.CONVERSION_FAILED)
        }
    }

    private external fun convert(sourcePath: String, outputDirectory: String): Int

    private const val STATUS_OK = 0
    private const val STATUS_INVALID = 1
    private const val STATUS_ENCRYPTED = 2
    private const val STATUS_PRINT_REPLICA = 3
    private const val STATUS_LIMIT = 4
}

enum class MobiFailure {
    INVALID_DOCUMENT,
    ENCRYPTED,
    PRINT_REPLICA,
    LIMIT_EXCEEDED,
    CONVERSION_FAILED
}

class MobiConversionException(val failure: MobiFailure) : IOException(failure.name)
