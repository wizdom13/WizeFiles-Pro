// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.remote

import android.os.IBinder
import android.os.IInterface
import android.os.RemoteException
import com.wisso.wizefiles.util.AppLog
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

class BinderEndpoint<T : IInterface>(private val connect: () -> T) : AutoCloseable {
    private val monitor = Any()
    private var service: T? = null
    private val listeners = linkedSetOf<() -> Unit>()
    private val deathRecipient: IBinder.DeathRecipient = WeakRecipient(this)

    fun isConnected(): Boolean = synchronized(monitor) { service != null }

    @Throws(BridgeUnavailableException::class)
    fun requireService(): T = synchronized(monitor) {
        service?.let { return@synchronized it }

        val candidate = connect()
        try {
            candidate.asBinder().linkToDeath(deathRecipient, 0)
        } catch (failure: RemoteException) {
            throw BridgeUnavailableException(failure)
        }
        service = candidate
        candidate
    }

    fun onBinderDeath(listener: () -> Unit): AutoCloseable {
        synchronized(monitor) { listeners += listener }
        val active = AtomicBoolean(true)
        return AutoCloseable {
            if (active.compareAndSet(true, false)) {
                synchronized(monitor) { listeners -= listener }
            }
        }
    }

    internal fun handleBinderDeath() {
        val callbacks = synchronized(monitor) {
            detachLocked()
            listeners.toList()
        }
        callbacks.forEach { callback ->
            runCatching(callback).onFailure { failure ->
                AppLog.w("BinderEndpoint", "Binder death callback failed", failure)
            }
        }
    }

    override fun close() {
        synchronized(monitor) {
            detachLocked()
            listeners.clear()
        }
    }

    private fun detachLocked() {
        val binder = service?.asBinder()
        service = null
        if (binder != null) {
            runCatching { binder.unlinkToDeath(deathRecipient, 0) }
        }
    }

    @Suppress("deprecation")
    protected fun finalize() {
        close()
    }

    private class WeakRecipient<T : IInterface>(
        endpoint: BinderEndpoint<T>
    ) : IBinder.DeathRecipient {
        private val endpoint = WeakReference(endpoint)

        override fun binderDied() {
            endpoint.get()?.handleBinderDeath()
        }
    }
}
