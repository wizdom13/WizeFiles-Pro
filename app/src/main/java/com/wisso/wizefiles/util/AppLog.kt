// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.wisso.wizefiles.BuildConfig
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLocalFileOrNull
import java.util.concurrent.Executors

object AppLog {
    private const val BASE_TAG = "WizeFiles"
    private const val INTERNAL_TAG = "AppLog"

    @Volatile
    private var store: DebugLogStore? = null

    @Volatile
    var minLevel: Level = if (BuildConfig.DEBUG) Level.DEBUG else Level.INFO

    private val logWriterExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AppLogWriter").apply { isDaemon = true }
    }

    fun initialize(context: Context) {
        if (store != null) {
            return
        }
        synchronized(this) {
            if (store != null) {
                return
            }
            store = DebugLogStore(context.filesDir.resolve("debug_logs"))
            i(INTERNAL_TAG, "Logger initialized")
        }
    }

    fun d(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.DEBUG, tag, message, throwable)

    fun i(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.INFO, tag, message, throwable)

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.WARN, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.ERROR, tag, message, throwable)

    fun sanitize(message: String): String = DebugLogSanitizer.sanitize(message)

    fun summarizeIntent(intent: Intent?): String {
        if (intent == null) {
            return "intent=<none>"
        }
        val action = sanitize(intent.action.orEmpty())
        val data = sanitize(intent.dataString.orEmpty())
        val type = sanitize(intent.type.orEmpty())
        return "intent={action=$action,data=$data,type=$type,scheme=${intent.scheme.orEmpty()}}"
    }

    fun readRecentForCrashReport(maxChars: Int): String =
        store?.readTail(maxChars).orEmpty()

    fun exportTo(path: AppPath) {
        val logStore = store ?: return
        val file = checkNotNull(path.toLocalFileOrNull()) { "Unsupported export path: $path" }
        file.outputStream().use { outputStream ->
            logStore.exportTo(outputStream)
        }
    }

    private fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
        if (level.priority < minLevel.priority) {
            return
        }
        val sanitizedMessage = sanitize(message)
        val sanitizedThrowable = throwable?.let { sanitizeThrowable(it) }

        try {
            val line = DebugLogFormatter.format(level, tag, sanitizedMessage, sanitizedThrowable)
            store?.let { logStore ->
                logWriterExecutor.execute {
                    try {
                        logStore.appendLine(line)
                    } catch (e: Exception) {
                        Log.e("$BASE_TAG/$INTERNAL_TAG", "Failed to persist log entry", e)
                    }
                }
            }
            val logTag = "$BASE_TAG/$tag"
            if (sanitizedThrowable != null) {
                Log.println(level.priority, logTag, "$sanitizedMessage\n${Log.getStackTraceString(sanitizedThrowable)}")
            } else {
                Log.println(level.priority, logTag, sanitizedMessage)
            }
        } catch (e: Exception) {
            Log.e("$BASE_TAG/$INTERNAL_TAG", "Failed to write log", e)
        }
    }

    internal fun sanitizeThrowableForTest(throwable: Throwable): Throwable = sanitizeThrowable(throwable)

    private fun sanitizeThrowable(throwable: Throwable): Throwable {
        return sanitizeThrowable(throwable, 0, HashSet())
    }

    private fun sanitizeThrowable(
        throwable: Throwable,
        depth: Int,
        visited: MutableSet<Throwable>
    ): Throwable {
        val sanitizedMessage = throwable.message?.let { sanitize(it) }
        val sanitizedDescription = if (sanitizedMessage.isNullOrBlank()) {
            throwable.javaClass.name
        } else {
            "${throwable.javaClass.name}: $sanitizedMessage"
        }
        if (depth >= 16 || !visited.add(throwable)) {
            return RuntimeException(sanitizedDescription).also { it.stackTrace = throwable.stackTrace }
        }
        val sanitizedCause = throwable.cause?.let { sanitizeThrowable(it, depth + 1, visited) }
        val sanitized = RuntimeException(sanitizedDescription, sanitizedCause)
        throwable.suppressed.forEach { suppressed ->
            sanitized.addSuppressed(sanitizeThrowable(suppressed, depth + 1, visited))
        }
        sanitized.stackTrace = throwable.stackTrace
        return sanitized
    }

    enum class Level(val priority: Int) {
        DEBUG(Log.DEBUG),
        INFO(Log.INFO),
        WARN(Log.WARN),
        ERROR(Log.ERROR)
    }
}

private fun threadId(thread: Thread): Long {
    // Android does not expose a non-deprecated, universally available replacement for
    // java.lang.Thread#getId() on all supported API levels.
    @Suppress("DEPRECATION")
    return thread.id
}

internal object DebugLogSanitizer {
    private val keyValuePattern = Regex(
        """(?i)\b(password|passwd|token|secret|api[_-]?key|authorization|cookie|set-cookie|private[_-]?key(?:[_-]?password)?|passphrase)\b\s*[:=]\s*([^\s,;]+)"""
    )
    private val authorizationPattern = Regex("""(?i)(authorization\s*[:=]\s*)(bearer|basic)\s+([^\s,;]+)""")
    private val secretQueryPattern = Regex(
        """([?&](?:password|passwd|token|secret|apikey|api_key|access_token|refresh_token|auth|signature)=)([^&\s]+)""",
        RegexOption.IGNORE_CASE
    )
    private val uriCredentialsPattern = Regex("""(?i)\b([a-z][a-z0-9+.-]*://)([^\s/@:]+)(?::([^\s/@]*))?@""")
    private val privateKeyBlockPattern = Regex("""-----BEGIN [^-]*PRIVATE KEY-----[\s\S]*?-----END [^-]*PRIVATE KEY-----""")

    fun sanitize(message: String): String =
        message
            .replace(privateKeyBlockPattern, "<redacted-private-key>")
            .replace(authorizationPattern) { matchResult ->
                "${matchResult.groupValues[1]}${matchResult.groupValues[2]} <redacted>"
            }
            .replace(keyValuePattern) { matchResult ->
                "${matchResult.groupValues[1]}=<redacted>"
            }
            .replace(secretQueryPattern) { matchResult ->
                "${matchResult.groupValues[1]}<redacted>"
            }
            .replace(uriCredentialsPattern) { matchResult ->
                "${matchResult.groupValues[1]}<redacted>@"
            }
}

internal object DebugLogFormatter {
    fun format(level: AppLog.Level, tag: String, message: String, throwable: Throwable?): String {
        val timestamp = System.currentTimeMillis()
        val thread = Thread.currentThread()
        val builder = StringBuilder()
            .append(timestamp)
            .append(" ")
            .append(level.name)
            .append(" [")
            .append(thread.name)
            .append("#")
            .append(threadId(thread))
            .append("] ")
            .append(tag)
            .append(": ")
            .append(message)
        if (throwable != null) {
            builder
                .append('\n')
                .append(Log.getStackTraceString(throwable))
        }
        return builder.toString()
    }
}
