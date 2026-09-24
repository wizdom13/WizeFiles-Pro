// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.rclone

import android.content.Context
import android.system.Os
import com.wisso.wizefiles.security.SecretStore
import java.io.File
import java.io.IOException
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import org.rclone.gomobile.Gomobile
import org.rclone.gomobile.OAuthURLListener

/**
 * Stable WizeFiles boundary around rclone's intentionally low-level gomobile RPC API.
 *
 * All calls are serialized because rclone's config path and config cache are process-global.
 * The plaintext configuration exists only while an RPC is executing; its durable copy is held
 * by [SecretStore], which is backed by Android Keystore encrypted preferences.
 */
object RcloneEngine {
    private const val CONFIG_SECRET_KEY = "rclone.config.v1"

    private val lock = Any()
    private lateinit var configFile: File
    private lateinit var stagingDirectory: File
    private lateinit var secretStore: SecretStore
    private var initialized = false
    private var nativeInitialized = false

    fun initialize(context: Context, secretStore: SecretStore) {
        synchronized(lock) {
            if (initialized) {
                return
            }
            val appContext = context.applicationContext
            val runtimeDirectory = File(appContext.noBackupFilesDir, "rclone")
            stagingDirectory = File(appContext.cacheDir, "rclone-staging")
            runtimeDirectory.mkdirs()
            stagingDirectory.mkdirs()
            configFile = File(runtimeDirectory, "session.conf")
            // rclone resolves its default directories while the native library is loaded.
            // Android has no conventional home directory, so provide private app paths first.
            Os.setenv("HOME", runtimeDirectory.absolutePath, true)
            Os.setenv("XDG_CONFIG_HOME", runtimeDirectory.absolutePath, true)
            Os.setenv("XDG_CACHE_HOME", stagingDirectory.absolutePath, true)
            this.secretStore = secretStore
            // Remove plaintext left behind by a process crash before initializing rclone.
            configFile.delete()
            initialized = true
        }
    }

    fun version(): JSONObject = rpc("core/version")

    fun list(remoteName: String, path: String): List<RcloneEntry> {
        val output = rpc(
            "operations/list",
            JSONObject()
                .put("fs", remoteFs(remoteName))
                .put("remote", normalizeRemotePath(path))
                .put("opt", JSONObject().put("recurse", false).put("showHash", false))
        )
        return output.optJSONArray("list").orEmpty().mapObjects(RcloneEntry::fromJson)
    }

    fun stat(remoteName: String, path: String): RcloneEntry? {
        if (normalizeRemotePath(path).isEmpty()) {
            return RcloneEntry("", "", 0L, null, true)
        }
        return try {
            val output = rpc(
                "operations/stat",
                JSONObject()
                    .put("fs", remoteFs(remoteName))
                    .put("remote", normalizeRemotePath(path))
            )
            output.optJSONObject("item")?.let(RcloneEntry::fromJson)
        } catch (exception: RcloneException) {
            if (exception.isNotFound) null else throw exception
        }
    }

    fun createDirectory(remoteName: String, path: String) {
        rpc(
            "operations/mkdir",
            JSONObject()
                .put("fs", remoteFs(remoteName))
                .put("remote", normalizeRemotePath(path))
        )
    }

    fun deleteFile(remoteName: String, path: String) {
        rpc(
            "operations/deletefile",
            JSONObject()
                .put("fs", remoteFs(remoteName))
                .put("remote", normalizeRemotePath(path))
        )
    }

    fun deleteEmptyDirectory(remoteName: String, path: String) {
        if (list(remoteName, path).isNotEmpty()) {
            throw java.nio.file.DirectoryNotEmptyException(path)
        }
        rpc(
            "operations/rmdir",
            JSONObject()
                .put("fs", remoteFs(remoteName))
                .put("remote", normalizeRemotePath(path))
        )
    }

    fun copy(
        sourceRemote: String,
        sourcePath: String,
        targetRemote: String,
        targetPath: String
    ) {
        rpc(
            "operations/copyfile",
            JSONObject()
                .put("srcFs", remoteFs(sourceRemote))
                .put("srcRemote", normalizeRemotePath(sourcePath))
                .put("dstFs", remoteFs(targetRemote))
                .put("dstRemote", normalizeRemotePath(targetPath))
        )
    }

    fun move(
        sourceRemote: String,
        sourcePath: String,
        targetRemote: String,
        targetPath: String
    ) {
        rpc(
            "operations/movefile",
            JSONObject()
                .put("srcFs", remoteFs(sourceRemote))
                .put("srcRemote", normalizeRemotePath(sourcePath))
                .put("dstFs", remoteFs(targetRemote))
                .put("dstRemote", normalizeRemotePath(targetPath))
        )
    }

    fun download(remoteName: String, remotePath: String): File {
        val target = newStagingFile("download")
        try {
            transferFile(
                sourceFs = remoteFs(remoteName),
                sourcePath = normalizeRemotePath(remotePath),
                targetFs = target.parentFile!!.absolutePath,
                targetPath = target.name
            )
        } catch (throwable: Throwable) {
            target.delete()
            throw throwable
        }
        return target
    }

    fun upload(localFile: File, remoteName: String, remotePath: String) {
        transferFile(
            sourceFs = localFile.parentFile!!.absolutePath,
            sourcePath = localFile.name,
            targetFs = remoteFs(remoteName),
            targetPath = normalizeRemotePath(remotePath)
        )
    }

    private fun transferFile(
        sourceFs: String,
        sourcePath: String,
        targetFs: String,
        targetPath: String
    ) {
        rpc(
            "operations/copyfile",
            JSONObject()
                .put("srcFs", sourceFs)
                .put("srcRemote", sourcePath)
                .put("dstFs", targetFs)
                .put("dstRemote", targetPath)
        )
    }

    fun createRemote(remoteName: String, backendType: String, parameters: Map<String, String>) {
        val step = beginCreateRemote(
            remoteName = remoteName,
            backendType = backendType,
            parameters = parameters,
            askAll = false
        )
        if (!step.isComplete) {
            deleteRemote(remoteName)
            throw RcloneException(
                "config/create",
                409L,
                "Additional configuration is required"
            )
        }
    }

    fun listProviders(): List<RcloneProviderDefinition> =
        parseRcloneProviders(rpc("config/providers"))

    fun installOAuthURLListener(opener: (String) -> Boolean) {
        synchronized(lock) {
            ensureInitialized()
            initializeNative()
            Gomobile.setOAuthURLListener(
                object : OAuthURLListener {
                    override fun openOAuthURL(url: String): Boolean = opener(url)
                }
            )
        }
    }

    fun clearOAuthURLListener() {
        synchronized(lock) {
            if (nativeInitialized) {
                Gomobile.clearOAuthURLListener()
            }
        }
    }

    fun beginCreateRemote(
        remoteName: String,
        backendType: String,
        parameters: Map<String, String>,
        askAll: Boolean
    ): RcloneConfigStep =
        configureRemote(
            remoteName = remoteName,
            backendType = backendType,
            parameters = parameters,
            askAll = askAll
        )

    fun continueCreateRemote(
        remoteName: String,
        backendType: String,
        parameters: Map<String, String>,
        askAll: Boolean,
        state: String,
        result: String
    ): RcloneConfigStep =
        configureRemote(
            remoteName = remoteName,
            backendType = backendType,
            parameters = parameters,
            askAll = askAll,
            state = state,
            result = result
        )

    private fun configureRemote(
        remoteName: String,
        backendType: String,
        parameters: Map<String, String>,
        askAll: Boolean,
        state: String? = null,
        result: String? = null
    ): RcloneConfigStep {
        val parameterJson = JSONObject()
        parameters.filterValues { it.isNotBlank() }.forEach { (key, value) ->
            parameterJson.put(key, value)
        }
        val options = JSONObject()
            .put("obscure", true)
            .put("nonInteractive", true)
            .put("noOutput", true)
            .put("all", askAll)
        if (state != null) {
            options
                .put("continue", true)
                .put("state", state)
                .put("result", result.orEmpty())
        }
        return parseRcloneConfigStep(
            rpc(
                "config/create",
                JSONObject()
                    .put("name", remoteName)
                    .put("type", backendType)
                    .put("parameters", parameterJson)
                    .put("opt", options)
            )
        )
    }

    fun deleteRemote(remoteName: String) {
        rpc("config/delete", JSONObject().put("name", remoteName))
    }

    fun importRemote(configuration: String, sourceName: String, targetName: String) {
        synchronized(lock) {
            ensureInitialized()
            val importedSection = extractRemoteSection(configuration, sourceName)
                ?: throw IllegalArgumentException(
                    "Remote '$sourceName' is not present in the configuration"
                )
            val existing = secretStore.getSecret(CONFIG_SECRET_KEY).orEmpty()
            secretStore.putSecret(
                CONFIG_SECRET_KEY,
                appendRemoteSection(existing, targetName, importedSection)
            )
        }
    }

    fun listConfiguredRemotes(): List<String> =
        rpc("config/listremotes").optJSONArray("remotes").orEmpty().mapStrings()

    fun hasConfiguration(): Boolean =
        synchronized(lock) { secretStore.getSecret(CONFIG_SECRET_KEY) != null }

    fun newStagingFile(prefix: String): File {
        ensureInitialized()
        return File.createTempFile("$prefix-", ".tmp", stagingDirectory)
    }

    fun rpc(method: String, parameters: JSONObject = JSONObject()): JSONObject =
        synchronized(lock) {
            ensureInitialized()
            materializeConfiguration()
            try {
                initializeNative()
                val setPath = Gomobile.rcloneRPC(
                    "config/setpath",
                    JSONObject().put("path", configFile.absolutePath).toString()
                )
                if (setPath.status !in 200L..299L) {
                    throw RcloneException("config/setpath", setPath.status, setPath.output)
                }
                val result = Gomobile.rcloneRPC(method, parameters.toString())
                if (result.status !in 200L..299L) {
                    throw RcloneException(method, result.status, result.output)
                }
                JSONObject(result.output.ifBlank { "{}" })
            } finally {
                captureConfiguration()
                configFile.delete()
            }
        }

    private fun materializeConfiguration() {
        configFile.parentFile?.mkdirs()
        configFile.writeText(secretStore.getSecret(CONFIG_SECRET_KEY).orEmpty())
    }

    private fun captureConfiguration() {
        if (!configFile.isFile) {
            return
        }
        val configuration = configFile.readText()
        if (configuration.isNotBlank()) {
            secretStore.putSecret(CONFIG_SECRET_KEY, configuration)
        }
    }

    private fun ensureInitialized() {
        check(initialized) { "RcloneEngine must be initialized during application startup" }
    }

    private fun initializeNative() {
        if (!nativeInitialized) {
            val error = Gomobile.rcloneInitialize(
                configFile.absolutePath,
                stagingDirectory.absolutePath
            )
            check(error.isBlank()) { "Unable to initialize rclone: $error" }
            nativeInitialized = true
        }
    }

    private fun remoteFs(remoteName: String): String = "$remoteName:"

    private fun normalizeRemotePath(path: String): String = path.trimStart('/')
}

data class RcloneEntry(
    val path: String,
    val name: String,
    val size: Long,
    val modifiedAt: Instant?,
    val isDirectory: Boolean
) {
    companion object {
        fun fromJson(json: JSONObject): RcloneEntry =
            RcloneEntry(
                path = json.optString("Path"),
                name = json.optString("Name"),
                size = json.optLong("Size", 0L),
                modifiedAt = json.optString("ModTime")
                    .takeIf(String::isNotBlank)
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() },
                isDirectory = json.optBoolean("IsDir")
            )
    }
}

class RcloneException(
    val method: String,
    val status: Long,
    private val response: String
) : IOException(buildRcloneErrorMessage(method, status, response)),
    com.wisso.wizefiles.storage.ProviderFailureSignalSource {
    override val providerFailureSignal: com.wisso.wizefiles.storage.ProviderFailureSignal
        get() = when {
            status == 401L || status == 403L -> com.wisso.wizefiles.storage.ProviderFailureSignal.PERMISSION_REVOKED
            isNotFound -> com.wisso.wizefiles.storage.ProviderFailureSignal.STALE_RESOURCE
            status == 408L || status == 429L || status >= 500L ->
                com.wisso.wizefiles.storage.ProviderFailureSignal.UNAVAILABLE
            status in 400L..499L -> com.wisso.wizefiles.storage.ProviderFailureSignal.PERMANENT
            else -> com.wisso.wizefiles.storage.ProviderFailureSignal.MALFORMED_RESPONSE
        }
    val isNotFound: Boolean
        get() = status == 404L || response.contains("not found", ignoreCase = true)
}

private fun buildRcloneErrorMessage(method: String, status: Long, response: String): String {
    val normalizedResponse = response.trim()
    val detail = runCatching {
        JSONObject(normalizedResponse).optString("error")
    }.getOrNull()
        ?.takeIf(String::isNotBlank)
        ?: normalizedResponse
    val summary = detail.replace(Regex("""\s+"""), " ").take(500)
    return buildString {
        append("rclone ")
        append(method)
        append(" failed (")
        append(status)
        append(')')
        if (summary.isNotBlank()) {
            append(": ")
            append(summary)
        }
    }
}

private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    List(length()) { index -> transform(getJSONObject(index)) }

private fun JSONArray.mapStrings(): List<String> =
    List(length()) { index -> getString(index) }

internal fun extractRemoteSection(configuration: String, remoteName: String): String? {
    val header = Regex("""(?m)^\s*\[${Regex.escape(remoteName)}]\s*$""")
        .find(configuration)
        ?: return null
    val bodyStart = header.range.last + 1
    val nextHeader = Regex("""(?m)^\s*\[[^\]]+]\s*$""").find(configuration, bodyStart)
    return configuration.substring(bodyStart, nextHeader?.range?.first ?: configuration.length)
        .trim()
        .takeIf(String::isNotEmpty)
}

internal fun appendRemoteSection(
    configuration: String,
    remoteName: String,
    sectionBody: String
): String {
    require(Regex("""[A-Za-z0-9_-]+""").matches(remoteName)) { "Invalid remote identifier" }
    val separator = if (configuration.isBlank()) "" else "\n\n"
    return configuration.trimEnd() + separator +
        "[$remoteName]\n" + sectionBody.trim() + "\n"
}
