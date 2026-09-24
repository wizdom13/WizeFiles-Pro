// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.android.compat

import android.os.Build
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import com.wisso.wizefiles.util.AppLog
import com.wisso.wizefiles.util.lazyReflectedMethod
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicInteger

private val legacyVolumeList by lazyReflectedMethod(StorageManager::class.java, "getVolumeList")

val StorageManager.storageVolumesCompat: List<StorageVolume>
    get() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) return storageVolumes
        @Suppress("UNCHECKED_CAST")
        return (legacyVolumeList.invoke(this) as Array<StorageVolume>).asList()
    }

@Throws(IOException::class)
fun StorageManager.openProxyFileDescriptorCompat(
    mode: Int,
    callback: ProxyFileDescriptorCallbackCompat,
    handler: Handler
): ParcelFileDescriptor {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        return openProxyFileDescriptor(
            mode,
            callback.toProxyFileDescriptorCallback(),
            handler
        )
    }
    if (mode != ParcelFileDescriptor.MODE_READ_ONLY) {
        throw UnsupportedOperationException(
            "Legacy proxy descriptors support read-only access"
        )
    }
    val pipe = ParcelFileDescriptor.createReliablePipe()
    LegacyReadPipe(pipe[1], callback, handler).start()
    return pipe[0]
}

private class LegacyReadPipe(
    private val writeEnd: ParcelFileDescriptor,
    private val callback: ProxyFileDescriptorCallbackCompat,
    private val callbackHandler: Handler
) {
    fun start() {
        Thread(::pump, "WizeProxyPipe-" + nextId.getAndIncrement()).start()
    }

    private fun pump() {
        var failure: Throwable? = null
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var offset = 0L
                while (true) {
                    val count = onCallbackThread {
                        callback.onRead(offset, buffer.size, buffer)
                    }
                    if (count == 0) break
                    if (count !in 1..buffer.size) {
                        throw IOException("Proxy callback returned invalid byte count: " + count)
                    }
                    output.write(buffer, 0, count)
                    offset += count
                }
            }
        } catch (caught: Throwable) {
            failure = caught
            AppLog.e("StorageManagerCompat", "Legacy proxy pipe failed", caught)
            runCatching { writeEnd.closeWithError(caught.message) }
        } finally {
            runCatching { onCallbackThread(callback::onRelease) }
                .onFailure { releaseFailure ->
                    AppLog.e(
                        "StorageManagerCompat",
                        "Proxy release callback failed",
                        releaseFailure
                    )
                    if (failure == null) runCatching {
                        writeEnd.closeWithError(releaseFailure.message)
                    }
                }
        }
    }

    private fun <T> onCallbackThread(action: () -> T): T {
        val task = FutureTask<T> { action() }
        check(callbackHandler.post(task)) { "Callback handler is shutting down" }
        return try {
            task.get()
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        }
    }

    companion object {
        private const val BUFFER_SIZE = 16 * 1024
        private val nextId = AtomicInteger(1)
    }
}
