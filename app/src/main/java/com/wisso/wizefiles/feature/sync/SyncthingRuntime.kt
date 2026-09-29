// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.system.Os
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import org.json.JSONObject

internal fun interface SyncthingControl {
    fun request(method: String, path: String, body: String?): String
}

/** One private runtime per app process. No TCP control listener, WebView, shell or downloaded code. */
internal class SyncthingRuntime private constructor(context: Context,
    private val directory: File = File(context.noBackupFilesDir, "syncthing")) : SyncthingControl {
    private val app = context.applicationContext
    private val socketFile = File(directory, "api.sock")
    @Volatile private var child: Process? = null
    private var apiKey = ""
    @Volatile private var ready = false
    private var leases = 0
    @Volatile private var maintenance = false
    private val requests = java.util.concurrent.ConcurrentHashMap.newKeySet<LocalSocket>()

    @Synchronized
    fun acquire(): SyncthingRuntime {
        check(!maintenance) { "Syncthing configuration maintenance is in progress" }
        if (directory.name == "syncthing") {
            check(!SyncthingMigration.applying) { "Syncthing configuration import is in progress" }
            SyncthingMigration.recover(app)
        }
        start()
        leases++
        return this
    }

    @Synchronized
    fun release() {
        check(leases > 0)
        leases--
        if (leases == 0) stop()
    }

    private fun start() {
        if (ready && child?.isAlive == true) return
        directory.mkdirs()
        Os.chmod(directory.absolutePath, 448) // 0700; also protects the Unix control socket.
        val keyFile = File(directory, "api-key")
        apiKey = if (keyFile.isFile) keyFile.readText() else {
            ByteArray(32).also(SecureRandom()::nextBytes)
                .joinToString("") { "%02x".format(it.toInt() and 255) }.also {
                    val pendingKey = File(directory, "api-key.pending")
                    pendingKey.writeText(it)
                    Os.chmod(pendingKey.absolutePath, 384)
                    java.nio.file.Files.move(pendingKey.toPath(), keyFile.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                }
        }
        check(apiKey.matches(Regex("[a-f0-9]{64}"))) { "Invalid Syncthing control identity" }
        // A previous app process may have died before stopping its child. Shut that engine down
        // using its private authenticated socket before opening the same database again.
        if (socketFile.exists()) {
            runCatching { request("POST", "/rest/system/shutdown", null) }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (socketFile.exists() && System.nanoTime() < deadline) Thread.sleep(100)
        }
        val executable = File(app.applicationInfo.nativeLibraryDir, "libsyncthing.so")
        check(executable.isFile && executable.canExecute()) { "Embedded Syncthing engine is unavailable" }
        val launcher = File(app.applicationInfo.nativeLibraryDir, "libsyncthing-launcher.so")
        check(launcher.isFile && launcher.canExecute()) { "Embedded Syncthing supervisor is unavailable" }
        val config = File(directory, "config.xml")
        if (!config.isFile) {
            val generator = ProcessBuilder(launcher.absolutePath, android.os.Process.myPid().toString(),
                executable.absolutePath, "--home", directory.absolutePath, "generate")
                .redirectOutput(File("/dev/null")).redirectError(File("/dev/null")).start()
            if (!generator.waitFor(30, TimeUnit.SECONDS)) {
                generator.destroyForcibly()
                throw IOException("Syncthing identity generation timed out")
            }
            check(generator.exitValue() == 0) { "Syncthing identity generation failed" }
        }
        val pendingConfig = File(directory, "config.xml.pending")
        pendingConfig.writeText(SyncthingBootstrap.offline(config.readText()))
        Os.chmod(pendingConfig.absolutePath, 384)
        java.nio.file.Files.move(pendingConfig.toPath(), config.toPath(),
            java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        val process = ProcessBuilder(
            launcher.absolutePath, android.os.Process.myPid().toString(), executable.absolutePath, "--home", directory.absolutePath, "serve",
            "--no-browser", "--no-restart", "--no-upgrade", "--paused",
            "--gui-address=unix://${socketFile.absolutePath}", "--log-level=WARN"
        ).apply {
            environment()["STGUIAPIKEY"] = apiKey
            environment()["STNORESTART"] = "1"
            // Our native supervisor owns the process. Avoid an upstream monitor grandchild
            // that could otherwise survive the supervised process being killed.
            environment()["STMONITORED"] = "yes"
            redirectErrorStream(true)
        }.start()
        child = process
        // Drain without retaining document names, peer addresses or authentication material.
        Thread({
            try {
                process.inputStream.use { it.copyTo(object : java.io.OutputStream() {
                    override fun write(value: Int) = Unit
                    override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
                }) }
            } catch (_: IOException) {
                // Process.destroy can close the pipe while this reader is blocked. An
                // uncaught exception on an Android background thread would kill the app.
            }
        }, "syncthing-output").apply { isDaemon = true; start() }
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            var lastFailure: Exception? = null
            while (System.nanoTime() < deadline && process.isAlive) {
                val reachable = try {
                    request("GET", "/rest/system/status", null)
                    true
                } catch (failure: Exception) {
                    lastFailure = failure
                    false
                }
                if (reachable) {
                    val options = JSONObject(request("GET", "/rest/config/options", null))
                        .put("startBrowser", false).put("autoUpgradeIntervalH", 0)
                        .put("urAccepted", -1).put("crashReportingEnabled", false)
                    request("PUT", "/rest/config/options", options.toString())
                    ready = true
                    return
                }
                Thread.sleep(100)
            }
            throw IOException("Syncthing engine did not become ready", lastFailure)
        } catch (failure: Exception) {
            stop()
            throw failure
        }
    }

    override fun request(method: String, path: String, body: String?): String =
        LocalSocket().use { socket ->
            requests.add(socket)
            try {
            socket.connect(LocalSocketAddress(socketFile.absolutePath, LocalSocketAddress.Namespace.FILESYSTEM))
            // LocalSocket creates its file descriptor on connect. Setting options before
            // that throws "socket not created" instead of reaching the private engine.
            socket.soTimeout = if (path.startsWith("/rest/db/scan?")) 6 * 60 * 60 * 1000 else 30_000
            SyncthingHttp.write(socket.outputStream, method, path, apiKey, body)
            SyncthingHttp.read(socket.inputStream)
            } finally { requests.remove(socket) }
        }

    fun isRunning(): Boolean = ready && child?.isAlive == true
    fun isMaintaining(): Boolean = maintenance
    @Synchronized fun beginMaintenance() {
        check(!maintenance && leases == 0 && child == null) { "Another Syncthing management operation is active" }
        maintenance = true
    }
    @Synchronized fun endMaintenance() { maintenance = false }

    fun interruptRequests() {
        requests.toList().forEach { runCatching { it.close() } }
    }

    /** Fail closed even when another UI operation still holds a lease. */
    @Synchronized
    fun abort() { stop() }

    private fun stop() {
        ready = false
        interruptRequests()
        val process = child ?: return
        runCatching { request("POST", "/rest/system/shutdown", null) }
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroy()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
            }
        }
        child = null
    }

    companion object {
        internal fun staged(context: Context, directory: File): SyncthingRuntime {
            require(directory.canonicalFile == File(context.noBackupFilesDir, "syncthing-import-stage").canonicalFile)
            return SyncthingRuntime(context, directory)
        }
        @Volatile private var instance: SyncthingRuntime? = null
        fun get(context: Context): SyncthingRuntime = instance ?: synchronized(this) {
            instance ?: SyncthingRuntime(context).also { instance = it }
        }
    }
}
