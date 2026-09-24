package com.wisso.wizefiles.core.selinux

import android.system.ErrnoException

object SeLinuxBridge {
    init {
        System.loadLibrary("selinuxbridge")
    }

    @Throws(ErrnoException::class)
    external fun getFileContext(path: ByteArray): ByteArray

    @Throws(ErrnoException::class)
    external fun getEnforce(): Boolean

    external fun isSelinuxEnabled(): Boolean

    @Throws(ErrnoException::class)
    external fun lGetFileContext(path: ByteArray): ByteArray

    @Throws(ErrnoException::class)
    external fun lSetFileContext(path: ByteArray, context: ByteArray)

    @Throws(ErrnoException::class)
    external fun setFileContext(path: ByteArray, context: ByteArray)

    @Throws(ErrnoException::class)
    external fun restoreContext(path: ByteArray, flags: Int)
}
