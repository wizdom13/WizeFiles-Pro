package com.wisso.wizefiles.feature.packageinstaller

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class StoredPackageInstallOperation(
    val operationId: String,
    val displayName: String,
    val sourcePath: String,
    val packageName: String?,
    val stage: PackageInstallStage,
    val sessionId: Int?,
    val statusMessage: String?,
    val installObb: Boolean = true,
    val usePrivilegedInstaller: Boolean = false
)

class PackageInstallOperationStore(context: Context) {
    private val directory = File(context.filesDir, "package-installer/operations")

    @Synchronized
    fun save(operation: StoredPackageInstallOperation) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Unable to create package operation storage")
        }
        val target = file(operation.operationId)
        val temporary = File(directory, "${operation.operationId}.json.tmp")
        val json = JSONObject()
            .put("schema", SCHEMA_VERSION)
            .put("operationId", operation.operationId)
            .put("displayName", operation.displayName)
            .put("sourcePath", operation.sourcePath)
            .put("packageName", operation.packageName)
            .put("stage", operation.stage.name)
            .put("sessionId", operation.sessionId)
            .put("statusMessage", operation.statusMessage)
            .put("installObb", operation.installObb)
            .put("usePrivilegedInstaller", operation.usePrivilegedInstaller)
        FileOutputStream(temporary).use { output ->
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (exception: Exception) {
                temporary.delete()
                throw IOException("Unable to commit package operation state", exception)
            }
        } catch (exception: Exception) {
            temporary.delete()
            throw IOException("Unable to commit package operation state", exception)
        }
    }

    @Synchronized
    fun load(operationId: String): StoredPackageInstallOperation? {
        require(OPERATION_ID.matches(operationId)) { "Invalid package operation ID" }
        val target = file(operationId)
        if (!target.isFile) return null
        return runCatching {
            val json = JSONObject(target.readText())
            require(json.getInt("schema") in 1..SCHEMA_VERSION)
            StoredPackageInstallOperation(
                operationId = json.getString("operationId"),
                displayName = json.getString("displayName"),
                sourcePath = json.getString("sourcePath"),
                packageName = json.optString("packageName").takeIf(String::isNotBlank),
                stage = PackageInstallStage.valueOf(json.getString("stage")),
                sessionId = json.optInt("sessionId", -1).takeIf { it >= 0 },
                statusMessage = json.optString("statusMessage").takeIf(String::isNotBlank),
                installObb = json.optBoolean("installObb", true),
                usePrivilegedInstaller = json.optBoolean("usePrivilegedInstaller", false)
            ).also { require(it.operationId == operationId) }
        }.getOrNull()
    }

    @Synchronized
    fun delete(operationId: String) {
        require(OPERATION_ID.matches(operationId)) { "Invalid package operation ID" }
        file(operationId).delete()
    }

    private fun file(operationId: String): File {
        require(OPERATION_ID.matches(operationId)) { "Invalid package operation ID" }
        return File(directory, "$operationId.json")
    }

    private companion object {
        const val SCHEMA_VERSION = 2
        val OPERATION_ID = Regex("^[A-Za-z0-9-]{1,64}$")
    }
}
