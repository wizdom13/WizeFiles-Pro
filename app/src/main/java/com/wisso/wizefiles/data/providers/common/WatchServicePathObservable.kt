// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import java.nio.file.ClosedWatchServiceException
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class WatchServicePathObservable(path: Path, intervalMillis: Long) : AbstractPathObservable(
    intervalMillis
) {
    private val watchService: WatchService
    private val poller: Poller

    init {
        var watchService: WatchService? = null
        var poller: Poller? = null
        var successful = false
        try {
            watchService = path.fileSystem.newWatchService()
            this.watchService = watchService
            path.register(
                watchService, StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
            )
            poller = Poller()
            this.poller = poller
            poller.start()
            successful = true
        } finally {
            if (!successful) {
                poller?.interrupt()
                watchService?.close()
            }
        }
    }

    @Throws(IOException::class)
    override fun onCloseLocked() {
        poller.interrupt()
        watchService.close()
    }

    companion object {
        private val pollerId = AtomicInteger()
    }

    private inner class Poller : Thread(
        "WatchServicePathObservable.Poller-${pollerId.getAndIncrement()}"
    ) {
        init {
            isDaemon = true
        }

        override fun run() {
            while (true) {
                val key = try {
                    watchService.take()
                } catch (e: ClosedWatchServiceException) {
                    break
                } catch (e: InterruptedException) {
                    break
                }
                if (key.pollEvents().isNotEmpty()) {
                    notifyObservers()
                }
                if (!key.reset()) {
                    break
                }
            }
        }
    }
}
