// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Bounded HTTP framing shared by the private Unix socket client and host contract tests. */
internal object SyncthingHttp {
    const val MAX_BODY = 2 * 1024 * 1024
    private const val MAX_HEADERS = 16 * 1024

    fun component(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    fun write(output: OutputStream, method: String, path: String, key: String, body: String?) {
        require(method in setOf("GET", "POST", "PUT", "DELETE"))
        require(path.startsWith("/rest/") && path.all { it.code in 33..126 })
        require(key.matches(Regex("[a-f0-9]{64}")))
        val payload = body.orEmpty().toByteArray(Charsets.UTF_8)
        require(payload.size <= MAX_BODY)
        val headers = "$method $path HTTP/1.1\r\nHost: localhost\r\n" +
            "X-API-Key: $key\r\nConnection: close\r\nContent-Type: application/json\r\n" +
            "Content-Length: ${payload.size}\r\n\r\n"
        output.write(headers.toByteArray(Charsets.US_ASCII))
        // A zero-length LocalSocket write still calls sendmsg on Android. For an empty
        // request the server may already have replied and closed after receiving headers.
        if (payload.isNotEmpty()) output.write(payload)
        output.flush()
    }

    fun read(input: InputStream): String {
        var headerBytes = 0
        fun line(): String {
            val output = ByteArrayOutputStream()
            while (true) {
                if (++headerBytes > MAX_HEADERS) throw IOException("Syncthing headers exceed limit")
                val byte = input.read()
                if (byte < 0) throw EOFException("Incomplete Syncthing response")
                if (byte == 10) break
                output.write(byte)
            }
            return output.toString("US-ASCII").removeSuffix("\r")
        }
        val status = line().split(' ').getOrNull(1)?.toIntOrNull()
            ?: throw IOException("Invalid Syncthing status")
        val headers = mutableMapOf<String, String>()
        while (true) {
            val value = line()
            if (value.isEmpty()) break
            val split = value.indexOf(':')
            if (split <= 0) throw IOException("Invalid Syncthing header")
            val name = value.substring(0, split).lowercase()
            if (name in headers) throw IOException("Duplicate Syncthing header")
            headers[name] = value.substring(split + 1).trim()
        }
        if ("transfer-encoding" in headers &&
            (!headers.getValue("transfer-encoding").equals("chunked", true) || "content-length" in headers)) {
            throw IOException("Ambiguous Syncthing response framing")
        }
        val output = ByteArrayOutputStream()
        fun copy(count: Long) {
            if (count < 0 || count > MAX_BODY - output.size()) throw IOException("Syncthing response exceeds limit")
            var remaining = count
            val buffer = ByteArray(8192)
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) throw EOFException("Incomplete Syncthing body")
                if (read == 0) continue
                output.write(buffer, 0, read)
                remaining -= read
            }
        }
        when {
            headers["transfer-encoding"]?.equals("chunked", true) == true -> {
                while (true) {
                    val count = line().substringBefore(';').toLongOrNull(16)
                        ?: throw IOException("Invalid Syncthing chunk")
                    if (count == 0L) break
                    copy(count)
                    if (line().isNotEmpty()) throw IOException("Invalid Syncthing chunk terminator")
                }
            }
            "content-length" in headers -> copy(headers.getValue("content-length").toLongOrNull()
                ?: throw IOException("Invalid Syncthing length"))
            status == 204 -> Unit
            else -> {
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_BODY) throw IOException("Syncthing response exceeds limit")
                    output.write(buffer, 0, count)
                }
            }
        }
        // Do not echo provider response bodies, which may contain local paths or configuration.
        if (status !in 200..299) throw IOException("Syncthing control request failed ($status)")
        return output.toString("UTF-8")
    }
}
