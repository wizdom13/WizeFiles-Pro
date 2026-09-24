package com.wisso.wizefiles.provider.common

import android.os.ParcelFileDescriptor
import java.nio.channels.FileChannel
import com.wisso.wizefiles.core.android.compat.NioUtilsCompat
import com.wisso.wizefiles.provider.os.syscall.Syscall
import com.wisso.wizefiles.provider.os.syscall.SyscallException
import java.io.Closeable
import java.io.FileDescriptor
import java.io.IOException
import kotlin.reflect.KClass

fun KClass<FileChannel>.open(fd: FileDescriptor, flags: Int): FileChannel {
    val closeable = Closeable {
        try {
            Syscall.close(fd)
        } catch (e: SyscallException) {
            throw IOException(e)
        }
    }
    return NioUtilsCompat.newFileChannel(closeable, fd, flags)
}

fun KClass<FileChannel>.open(pfd: ParcelFileDescriptor, mode: String): FileChannel =
    NioUtilsCompat.newFileChannel(
        pfd, pfd.fileDescriptor,
        ParcelFileDescriptor::class.modeToFlags(ParcelFileDescriptor.parseMode(mode))
    )
