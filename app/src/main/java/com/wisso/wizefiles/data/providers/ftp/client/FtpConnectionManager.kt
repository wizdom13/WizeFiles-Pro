package com.wisso.wizefiles.provider.ftp.client

import java.io.IOException
import java.util.IdentityHashMap
import org.apache.commons.net.ftp.FTPClient

/** Owns bounded, health-checked FTP control-connection pooling. */
internal class FtpConnectionManager(
    private val create: (Authority) -> FTPClient,
    private val dispose: (FTPClient) -> Unit,
    private val connectionLost: (Throwable) -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val pool = mutableMapOf<Authority, MutableList<FTPClient>>()
    private val releasedAt = IdentityHashMap<FTPClient, Long>()

    fun acquire(authority: Authority): FTPClient {
        while (true) {
            val client = take(authority) ?: return create(authority)
            if (!client.isConnected) {
                dispose(client)
                continue
            }
            val alive = try {
                client.sendNoOp()
            } catch (_: IOException) {
                false
            }
            if (alive) return client
            dispose(client)
        }
    }

    fun release(authority: Authority, client: FTPClient, failure: Throwable? = null) {
        if (failure != null && connectionLost(failure)) return dispose(client)
        if (!client.isConnected) return dispose(client)
        evictExpired().forEach(dispose)
        val keep = synchronized(pool) {
            val clients = pool.getOrPut(authority) { mutableListOf() }
            if (clients.size >= MAX_IDLE_PER_AUTHORITY) false else {
                clients += client
                releasedAt[client] = nowMillis()
                true
            }
        }
        if (!keep) dispose(client)
    }

    fun discard(client: FTPClient) = dispose(client)

    fun close(authority: Authority) {
        synchronized(pool) {
            pool.remove(authority).orEmpty().also { it.forEach(releasedAt::remove) }
        }.forEach(dispose)
    }

    fun closeAll() {
        synchronized(pool) {
            pool.values.flatten().also {
                pool.clear()
                it.forEach(releasedAt::remove)
            }
        }.forEach(dispose)
    }

    internal fun replaceIdle(authority: Authority, clients: List<FTPClient>) = synchronized(pool) {
        pool[authority] = clients.toMutableList()
        clients.forEach { releasedAt[it] = nowMillis() }
    }

    internal fun idle(authority: Authority): List<FTPClient> = synchronized(pool) {
        pool[authority].orEmpty().toList()
    }

    private fun take(authority: Authority): FTPClient? {
        evictExpired().forEach(dispose)
        return synchronized(pool) {
            val clients = pool[authority] ?: return null
            clients.removeLastOrNull().also { client ->
                client?.let(releasedAt::remove)
                if (clients.isEmpty()) pool -= authority
            }
        }
    }

    private fun evictExpired(): List<FTPClient> = synchronized(pool) {
        val now = nowMillis()
        val expired = mutableListOf<FTPClient>()
        val authorities = pool.iterator()
        while (authorities.hasNext()) {
            val entry = authorities.next()
            val clients = entry.value.iterator()
            while (clients.hasNext()) {
                val client = clients.next()
                if (now - (releasedAt[client] ?: now) >= IDLE_TIMEOUT_MILLIS) {
                    clients.remove()
                    releasedAt.remove(client)
                    expired += client
                }
            }
            if (entry.value.isEmpty()) authorities.remove()
        }
        expired
    }

    private companion object {
        const val IDLE_TIMEOUT_MILLIS = 60_000L
        const val MAX_IDLE_PER_AUTHORITY = 2
    }
}
