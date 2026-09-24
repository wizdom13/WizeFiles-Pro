// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.android.compat

import android.os.Build
import android.os.ProxyFileDescriptorCallback
import android.system.ErrnoException
import android.system.OsConstants
import androidx.annotation.RequiresApi

abstract class ProxyFileDescriptorCallbackCompat {
    @Throws(ErrnoException::class)
    open fun onGetSize(): Long = unsupported("onGetSize", OsConstants.EBADF)

    @Throws(ErrnoException::class)
    open fun onRead(offset: Long, size: Int, data: ByteArray): Int =
        unsupported("onRead", OsConstants.EBADF)

    @Throws(ErrnoException::class)
    open fun onWrite(offset: Long, size: Int, data: ByteArray): Int =
        unsupported("onWrite", OsConstants.EBADF)

    @Throws(ErrnoException::class)
    open fun onFsync(): Unit = unsupported("onFsync", OsConstants.EINVAL)

    abstract fun onRelease()

    @RequiresApi(Build.VERSION_CODES.O)
    fun toProxyFileDescriptorCallback(): ProxyFileDescriptorCallback = PlatformCallback(this)

    private fun <T> unsupported(operation: String, errno: Int): T {
        throw ErrnoException(operation, errno)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private class PlatformCallback(
        private val delegate: ProxyFileDescriptorCallbackCompat
    ) : ProxyFileDescriptorCallback() {
        override fun onGetSize(): Long = delegate.onGetSize()

        override fun onRead(offset: Long, size: Int, data: ByteArray): Int =
            delegate.onRead(offset, size, data)

        override fun onWrite(offset: Long, size: Int, data: ByteArray): Int =
            delegate.onWrite(offset, size, data)

        override fun onFsync() = delegate.onFsync()

        override fun onRelease() = delegate.onRelease()
    }
}
