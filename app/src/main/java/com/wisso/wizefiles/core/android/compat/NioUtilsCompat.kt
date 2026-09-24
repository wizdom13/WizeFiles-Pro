// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.android.compat

import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import com.wisso.wizefiles.hiddenapi.RestrictedHiddenApi
import com.wisso.wizefiles.util.lazyReflectedMethod
import java.io.Closeable
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel

object NioUtilsCompat {
    // There is no complete public API equivalent for creating a seekable read/write FileChannel
    // from a raw FileDescriptor with Linux open(2) flags. We use public read-only/write-only
    // channels as fallback and keep hidden-API usage only for the read/write case.
    @RestrictedHiddenApi
    private val newFileChannelMethod by lazyReflectedMethod(
        "java.nio.NioUtils", "newFileChannel", Closeable::class.java, FileDescriptor::class.java,
        Int::class.java
    )
    @RestrictedHiddenApi
    private val fileChannelImplOpenMethod by lazyReflectedMethod(
        "sun.nio.ch.FileChannelImpl", "open", FileDescriptor::class.java, String::class.java,
        Boolean::class.java, Boolean::class.java, Boolean::class.java, Any::class.java
    )

    fun newFileChannel(ioObject: Closeable, fd: FileDescriptor, flags: Int): FileChannel {
        val readable = flags and OsConstants.O_ACCMODE != OsConstants.O_WRONLY
        val writable = flags and OsConstants.O_ACCMODE != OsConstants.O_RDONLY
        if (!readable || !writable) {
            return newFileChannelPublicFallback(ioObject, fd, readable, writable, null)
        }
        return runCatching {
            newFileChannelHiddenApi(ioObject, fd, flags, readable, writable)
        }.getOrElse { newFileChannelPublicFallback(ioObject, fd, readable, writable, it) }
    }

    private fun newFileChannelHiddenApi(
        ioObject: Closeable,
        fd: FileDescriptor,
        flags: Int,
        readable: Boolean,
        writable: Boolean
    ): FileChannel =
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.N..<Build.VERSION_CODES.R) {
            // They broke O_RDONLY by assuming it's non-zero, but in fact it is zero.
            // https://android.googlesource.com/platform/libcore/+/nougat-release/luni/src/main/java/java/nio/NioUtils.java#63
            val append = flags and OsConstants.O_APPEND == OsConstants.O_APPEND
            fileChannelImplOpenMethod.invoke(
                null, fd, null, readable, writable, append, ioObject
            ) as FileChannel
        } else {
            newFileChannelMethod.invoke(null, ioObject, fd, flags) as FileChannel
        }

    private fun newFileChannelPublicFallback(
        ioObject: Closeable,
        fd: FileDescriptor,
        readable: Boolean,
        writable: Boolean,
        cause: Throwable?
    ): FileChannel {
        if (readable && writable) {
            throw UnsupportedOperationException(
                "Read/write FileChannel from FileDescriptor requires hidden API on this platform",
                cause
            )
        }
        if (ioObject is ParcelFileDescriptor) {
            return if (writable) {
                ParcelFileDescriptor.AutoCloseOutputStream(ioObject).channel
            } else {
                ParcelFileDescriptor.AutoCloseInputStream(ioObject).channel
            }
        }
        return if (writable) {
            FileOutputStream(fd).channel
        } else {
            FileInputStream(fd).channel
        }
    }
}
