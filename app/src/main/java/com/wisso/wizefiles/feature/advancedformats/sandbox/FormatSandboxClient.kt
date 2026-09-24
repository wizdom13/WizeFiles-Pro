package com.wisso.wizefiles.feature.advancedformats.sandbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import java.io.Closeable

/** Main-process connection that reconnects after an isolated parser crash. */
class FormatSandboxClient(
    context: Context,
    private val listener: Listener
) : Closeable {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var service: IFormatSandboxService? = null
    private var serviceBinder: IBinder? = null
    private var bound = false
    private var closed = false
    private var reconnectAttempt = 0

    private val deathRecipient = IBinder.DeathRecipient {
        mainHandler.post { handleDisconnect(reconnect = true) }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            synchronized(lock) {
                if (closed) return
                service = IFormatSandboxService.Stub.asInterface(binder)
                serviceBinder = binder
                reconnectAttempt = 0
            }
            runCatching { binder.linkToDeath(deathRecipient, 0) }
                .onFailure { handleDisconnect(reconnect = true) }
                .onSuccess { listener.onConnectionChanged(true) }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            handleDisconnect(reconnect = true)
        }

        override fun onBindingDied(name: ComponentName) {
            handleDisconnect(reconnect = true)
        }

        override fun onNullBinding(name: ComponentName) {
            handleDisconnect(reconnect = true)
        }
    }

    init {
        requestBind()
    }

    fun submit(
        request: FormatSandboxRequest,
        input: ParcelFileDescriptor,
        output: ParcelFileDescriptor,
        callback: IFormatSandboxCallback
    ): Long {
        val connectedService = synchronized(lock) { service }
            ?: throw IllegalStateException("Format sandbox is not connected")
        return connectedService.submit(request, input, output, callback)
    }

    fun cancel(requestId: Long) {
        synchronized(lock) { service }?.cancel(requestId)
    }

    override fun close() {
        mainHandler.post {
            val shouldUnbind = synchronized(lock) {
                if (closed) return@synchronized false
                closed = true
                serviceBinder?.unlinkToDeath(deathRecipient, 0)
                serviceBinder = null
                service = null
                bound.also { bound = false }
            }
            if (shouldUnbind) runCatching { appContext.unbindService(connection) }
            listener.onConnectionChanged(false)
        }
    }

    private fun requestBind(delayMillis: Long = 0L) {
        mainHandler.postDelayed({
            val shouldBind = synchronized(lock) { !closed && !bound }
            if (!shouldBind) return@postDelayed
            val didBind = appContext.bindService(
                Intent(appContext, FormatSandboxService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
            synchronized(lock) { bound = didBind }
            if (!didBind) scheduleReconnect()
        }, delayMillis)
    }

    private fun handleDisconnect(reconnect: Boolean) {
        val shouldUnbind = synchronized(lock) {
            serviceBinder?.unlinkToDeath(deathRecipient, 0)
            serviceBinder = null
            service = null
            bound.also { bound = false }
        }
        if (shouldUnbind) runCatching { appContext.unbindService(connection) }
        listener.onConnectionChanged(false)
        if (reconnect) scheduleReconnect()
    }

    private fun scheduleReconnect() {
        val delay = synchronized(lock) {
            if (closed) return
            reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(MAX_RECONNECT_ATTEMPTS)
            BASE_RECONNECT_DELAY_MILLIS * reconnectAttempt
        }
        requestBind(delay)
    }

    fun interface Listener {
        fun onConnectionChanged(connected: Boolean)
    }

    companion object {
        private const val BASE_RECONNECT_DELAY_MILLIS = 250L
        private const val MAX_RECONNECT_ATTEMPTS = 8
    }
}
