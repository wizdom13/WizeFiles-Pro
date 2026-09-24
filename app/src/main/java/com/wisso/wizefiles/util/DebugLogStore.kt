package com.wisso.wizefiles.util

import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.io.FileOutputStream

internal class DebugLogStore(
    private val directory: File,
    private val maxBytes: Long = 2L * 1024 * 1024
) {
    private val lock = Any()
    private val currentFile = directory.resolve("app-current.log")
    private val rotatedFile = directory.resolve("app-previous.log")

    fun appendLine(line: String) {
        val bytes = "$line\n".toByteArray(StandardCharsets.UTF_8)
        synchronized(lock) {
            ensureDirectory()
            rotateIfNeeded(bytes.size.toLong())
            FileOutputStream(currentFile, true).use { it.write(bytes) }
        }
    }

    fun exportTo(outputStream: OutputStream) {
        synchronized(lock) {
            ensureDirectory()
            if (rotatedFile.exists()) {
                rotatedFile.inputStream().use { it.copyTo(outputStream) }
            }
            if (currentFile.exists()) {
                currentFile.inputStream().use { it.copyTo(outputStream) }
            }
        }
    }

    fun readAll(): String {
        val output = StringBuilder()
        synchronized(lock) {
            if (rotatedFile.exists()) {
                output.append(rotatedFile.readText(StandardCharsets.UTF_8))
            }
            if (currentFile.exists()) {
                output.append(currentFile.readText(StandardCharsets.UTF_8))
            }
        }
        return output.toString()
    }

    fun readTail(maxChars: Int): String {
        require(maxChars >= 0) { "maxChars must not be negative" }
        if (maxChars == 0) return ""
        val contents = readAll()
        return if (contents.length <= maxChars) contents else contents.takeLast(maxChars)
    }

    private fun rotateIfNeeded(incomingSize: Long) {
        val currentSize = if (currentFile.exists()) currentFile.length() else 0L
        if (currentSize + incomingSize <= maxBytes) {
            return
        }
        if (rotatedFile.exists()) {
            rotatedFile.delete()
        }
        if (currentFile.exists()) {
            currentFile.renameTo(rotatedFile)
        }
        if (currentFile.exists()) {
            currentFile.delete()
        }
    }

    private fun ensureDirectory() {
        if (!directory.exists()) {
            directory.mkdirs()
        }
    }
}
