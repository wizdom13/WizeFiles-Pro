// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.smb

import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.FileNotifyAction
import com.hierynomus.mssmb2.SMB2CompletionFilter
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.mssmb2.messages.SMB2ChangeNotifyResponse
import com.hierynomus.smbj.share.Directory
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import com.wisso.wizefiles.provider.FileSystemProviders
import com.wisso.wizefiles.provider.common.AbstractWatchService
import com.wisso.wizefiles.provider.smb.client.SmbClient
import com.wisso.wizefiles.provider.smb.client.SmbClientException
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

// @see https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-smb2/05869c32-39f0-4726-afc9-671b76ae5ca7
internal class SmbWatchService : AbstractWatchService<SmbWatchKey>() {
    private val notifiers = mutableMapOf<SmbPath, Notifier>()

    @Throws(IOException::class)
    fun register(
        path: SmbPath,
        kinds: Array<WatchEvent.Kind<*>>,
        vararg modifiers: WatchEvent.Modifier
    ): SmbWatchKey {
        val kindSet = mutableSetOf<WatchEvent.Kind<*>>()
        for (kind in kinds) {
            when (kind) {
                StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY -> kindSet += kind
                // Ignored.
                StandardWatchEventKinds.OVERFLOW -> {}
                else -> throw UnsupportedOperationException(kind.name())
            }
        }
        for (modifier in modifiers) {
            throw UnsupportedOperationException(modifier.name())
        }
        synchronized(notifiers) {
            var notifier = notifiers[path]
            if (notifier != null) {
                notifier.kinds = kindSet
            } else {
                notifier = Notifier(this, path, kindSet)
                notifiers[path] = notifier
                notifier.start()
            }
            return notifier.key
        }
    }

    private fun removeNotifier(notifier: Notifier) {
        synchronized(notifiers) { notifiers -= notifier.key.watchable() }
    }

    override fun cancel(key: SmbWatchKey) {
        val notifier = synchronized(notifiers) { notifiers.remove(key.watchable()) } ?: return
        try {
            notifier.shutdownBounded()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            com.wisso.wizefiles.util.AppLog.w("SmbWatchService", "Shutdown interrupted", e)
        }
    }

    @Throws(IOException::class)
    override fun onClose() {
        // Don't keep synchronized on notifiers, or we may get a deadlock when joining.
        val notifiers = synchronized(notifiers) {
            notifiers.values.toList().also { notifiers.clear() }
        }
        var exception: IOException? = null
        for (notifier in notifiers) {
            try {
                if (!notifier.shutdownBounded()) {
                    val timeout = IOException(
                        "SMB watch shutdown exceeded ${SHUTDOWN_TIMEOUT_MILLIS}ms"
                    )
                    if (exception == null) exception = timeout else exception.addSuppressed(timeout)
                }
            } catch (e: InterruptedException) {
                val newException = InterruptedIOException().apply { initCause(e) }
                if (exception == null) {
                    exception = newException
                } else {
                    exception.addSuppressed(newException)
                }
            }
        }
        exception?.let { throw it }
    }

    private fun Notifier.shutdownBounded(): Boolean {
        val stopped = runSmbWatchShutdown(
            interrupt = ::interrupt,
            closeDirectory = ::closeDirectoryForShutdown,
            awaitShutdown = { timeout -> awaitThreadShutdown(this, timeout) },
            timeoutMillis = SHUTDOWN_TIMEOUT_MILLIS
        )
        if (!stopped) {
            com.wisso.wizefiles.util.AppLog.w(
                "SmbWatchService",
                "Notifier did not stop within ${SHUTDOWN_TIMEOUT_MILLIS}ms"
            )
        }
        return stopped
    }

    private class Notifier @Throws(IOException::class) constructor(
        private val watchService: SmbWatchService,
        path: SmbPath,
        @Volatile
        var kinds: Set<WatchEvent.Kind<*>>
    ) : Thread("SmbWatchService.Notifier-${id.getAndIncrement()}") {
        val key = SmbWatchKey(watchService, path)

        private val directory: Directory

        @Volatile
        private var future: Future<SMB2ChangeNotifyResponse>

        init {
            isDaemon = true
            try {
                directory = SmbClient.openDirectoryForChangeNotification(path)
                future = SmbClient.requestDirectoryChangeNotification(directory, COMPLETION_FILTER)
            } catch (e: SmbClientException) {
                throw e.toFileSystemException(path.toString())
            }
        }

        override fun run() {
            try {
                loop@ while (true) {
                    val response = future.get()
                    when (response.header.statusCode) {
                        NtStatus.STATUS_NOTIFY_ENUM_DIR.value ->
                            key.addEvent(StandardWatchEventKinds.OVERFLOW, null)
                        NtStatus.STATUS_SUCCESS.value -> {
                            if (FileSystemProviders.overflowWatchEvents) {
                                key.addEvent(StandardWatchEventKinds.OVERFLOW, null)
                            } else {
                                for (fileNotifyInfo in response.fileNotifyInfoList) {
                                    val kind = fileNotifyInfo.action.toEventKind()
                                    if (kind !in kinds) {
                                        continue
                                    }
                                    val name = key.watchable().fileSystem
                                        .getPath(fileNotifyInfo.fileName)
                                    key.addEvent(kind, name)
                                }
                            }
                        }
                        else ->
                            throw SMBApiException(
                                response.header, "Change notify failed for ${key.watchable()}"
                            )
                    }
                    future = SmbClient.requestDirectoryChangeNotification(directory, COMPLETION_FILTER)
                }
            } catch (e: Exception) {
                val interrupted = e.isExpectedSmbWatchInterruption()
                if (interrupted) {
                    Thread.currentThread().interrupt()
                } else {
                    com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
                }
                key.setInvalid()
                if (!interrupted) {
                    key.signal()
                }
                watchService.removeNotifier(this)
            } finally {
                closeDirectoryForShutdown()
            }
        }

        /** Closing the directory unblocks CHANGE_NOTIFY without SMBJ's broken SMB2_CANCEL path. */
        fun closeDirectoryForShutdown() {
            try {
                directory.close()
            } catch (e: Exception) {
                if (e.isExpectedSmbWatchInterruption()) {
                    interrupt()
                } else {
                    com.wisso.wizefiles.util.AppLog.e("SmbWatchService", "Directory close failed", e)
                }
            }
        }

        private fun FileNotifyAction.toEventKind(): WatchEvent.Kind<Path> =
            when (this) {
                FileNotifyAction.FILE_ACTION_ADDED, FileNotifyAction.FILE_ACTION_RENAMED_NEW_NAME ->
                    StandardWatchEventKinds.ENTRY_CREATE
                FileNotifyAction.FILE_ACTION_REMOVED,
                FileNotifyAction.FILE_ACTION_RENAMED_OLD_NAME ->
                    StandardWatchEventKinds.ENTRY_DELETE
                FileNotifyAction.FILE_ACTION_MODIFIED -> StandardWatchEventKinds.ENTRY_MODIFY
                else -> throw AssertionError(this)
            }

        companion object {
            private val COMPLETION_FILTER = setOf(
                SMB2CompletionFilter.FILE_NOTIFY_CHANGE_FILE_NAME,
                SMB2CompletionFilter.FILE_NOTIFY_CHANGE_DIR_NAME,
                SMB2CompletionFilter.FILE_NOTIFY_CHANGE_ATTRIBUTES,
                SMB2CompletionFilter.FILE_NOTIFY_CHANGE_SIZE,
                SMB2CompletionFilter.FILE_NOTIFY_CHANGE_LAST_WRITE,
                // We don't care about last access time and it might change too frequently.
                //SMB2CompletionFilter.FILE_NOTIFY_CHANGE_LAST_ACCESS,
                SMB2CompletionFilter.FILE_NOTIFY_CHANGE_CREATION,
                SMB2CompletionFilter.FILE_NOTIFY_CHANGE_EA,
                SMB2CompletionFilter.FILE_NOTIFY_CHANGE_SECURITY
            )

            private val id = AtomicInteger()
        }
    }

    companion object {
        internal const val SHUTDOWN_TIMEOUT_MILLIS = 5_000L
    }
}

internal fun Throwable.isExpectedSmbWatchInterruption(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is InterruptedException || current is InterruptedIOException) {
            return true
        }
        if (current.message?.contains("Got interrupted waiting for", ignoreCase = true) == true) {
            return true
        }
        current = current.cause
    }
    return false
}

internal fun awaitThreadShutdown(thread: Thread, timeoutMillis: Long): Boolean {
    thread.join(timeoutMillis)
    return !thread.isAlive
}

internal data class SmbWatchLifecycleSnapshot(
    val attempts: Long,
    val completed: Long,
    val timedOut: Long,
    val lastDurationMillis: Long
)

internal object SmbWatchLifecycleTelemetry {
    private val attempts = AtomicLong()
    private val completed = AtomicLong()
    private val timedOut = AtomicLong()
    private val lastDurationMillis = AtomicLong()

    fun record(stopped: Boolean, durationMillis: Long) {
        attempts.incrementAndGet()
        if (stopped) completed.incrementAndGet() else timedOut.incrementAndGet()
        lastDurationMillis.set(durationMillis)
    }

    fun snapshot() = SmbWatchLifecycleSnapshot(
        attempts.get(), completed.get(), timedOut.get(), lastDurationMillis.get()
    )

    internal fun resetForTest() {
        attempts.set(0)
        completed.set(0)
        timedOut.set(0)
        lastDurationMillis.set(0)
    }
}

internal fun runSmbWatchShutdown(
    interrupt: () -> Unit,
    closeDirectory: () -> Unit,
    awaitShutdown: (Long) -> Boolean,
    timeoutMillis: Long,
    nanoTime: () -> Long = System::nanoTime
): Boolean {
    require(timeoutMillis > 0)
    val started = nanoTime()
    interrupt()
    closeDirectory()
    val stopped = awaitShutdown(timeoutMillis)
    val durationMillis = ((nanoTime() - started).coerceAtLeast(0L)) / 1_000_000L
    SmbWatchLifecycleTelemetry.record(stopped, durationMillis)
    return stopped
}
