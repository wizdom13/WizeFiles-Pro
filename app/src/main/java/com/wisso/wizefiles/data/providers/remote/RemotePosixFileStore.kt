package com.wisso.wizefiles.provider.remote

import java.nio.file.attribute.FileAttributeView
import com.wisso.wizefiles.provider.common.PosixFileStore
import java.io.IOException

abstract class RemotePosixFileStore(
    private val remoteInterface: BinderEndpoint<IPosixStoreBridge>
) : PosixFileStore() {
    override fun refresh() {
        throw AssertionError()
    }

    override fun name(): String {
        throw AssertionError()
    }

    override fun type(): String {
        throw AssertionError()
    }

    override fun isReadOnly(): Boolean {
        throw AssertionError()
    }

    @Throws(IOException::class)
    override fun setReadOnly(readOnly: Boolean) {
        remoteInterface.requireService().invokeBridge { exception -> updateReadOnly(readOnly, exception) }
    }

    @Throws(IOException::class)
    override fun getTotalSpace(): Long =
        remoteInterface.requireService().invokeBridge { exception -> totalBytes(exception) }

    @Throws(IOException::class)
    override fun getUsableSpace(): Long =
        remoteInterface.requireService().invokeBridge { exception -> usableBytes(exception) }

    @Throws(IOException::class)
    override fun getUnallocatedSpace(): Long =
        remoteInterface.requireService().invokeBridge { exception -> unallocatedBytes(exception) }

    override fun supportsFileAttributeView(type: Class<out FileAttributeView>): Boolean {
        throw AssertionError()
    }

    override fun supportsFileAttributeView(name: String): Boolean {
        throw AssertionError()
    }
}
