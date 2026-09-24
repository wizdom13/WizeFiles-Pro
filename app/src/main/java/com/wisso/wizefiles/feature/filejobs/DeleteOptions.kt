package com.wisso.wizefiles.feature.filejobs

import android.os.Parcelable
import com.wisso.wizefiles.core.app.application
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.provider.document.isDocumentPath
import com.wisso.wizefiles.provider.ftp.isFtpPath
import com.wisso.wizefiles.provider.os.isLinuxPath
import com.wisso.wizefiles.provider.rclone.isRclonePath
import com.wisso.wizefiles.provider.sftp.isSftpPath
import com.wisso.wizefiles.provider.smb.isSmbPath

@Parcelize
data class DeleteOptions(
    val permanentDelete: Boolean = false,
    val skipConfirmationForSession: Boolean = false,
    val secureShred: Boolean = false
) : Parcelable {
    val directDelete: Boolean
        get() = permanentDelete || secureShred

    fun toRuntimeOptions(): DeleteOptions =
        if (skipConfirmationForSession) this else copy(skipConfirmationForSession = false)
}

enum class DeleteTargetMode {
    LOCAL_TRASH,
    PROVIDER_MANAGED,
    PERMANENT_ONLY,
    MIXED
}

internal enum class DeleteBackend {
    LOCAL,
    RCLONE,
    FTP,
    SFTP,
    SMB,
    SAF,
    ARCHIVE,
    OTHER_PROVIDER
}

object DeleteOptionsSupport {
    internal fun backend(path: Path): DeleteBackend = when {
        path.isLinuxPath || runCatching {
            path.fileSystem.provider().scheme.equals("file", ignoreCase = true)
        }.getOrDefault(false) -> DeleteBackend.LOCAL
        path.isRclonePath -> DeleteBackend.RCLONE
        path.isFtpPath -> DeleteBackend.FTP
        path.isSftpPath -> DeleteBackend.SFTP
        path.isSmbPath -> DeleteBackend.SMB
        path.isDocumentPath -> DeleteBackend.SAF
        path.isArchivePath -> DeleteBackend.ARCHIVE
        else -> DeleteBackend.OTHER_PROVIDER
    }

    internal fun targetModeForBackends(backends: List<DeleteBackend>): DeleteTargetMode {
        if (backends.isEmpty()) return DeleteTargetMode.PERMANENT_ONLY
        val modes = backends.mapTo(linkedSetOf()) { backend ->
            when (backend) {
                DeleteBackend.LOCAL -> DeleteTargetMode.LOCAL_TRASH
                DeleteBackend.RCLONE -> DeleteTargetMode.PROVIDER_MANAGED
                DeleteBackend.FTP,
                DeleteBackend.SFTP,
                DeleteBackend.SMB,
                DeleteBackend.SAF,
                DeleteBackend.ARCHIVE,
                DeleteBackend.OTHER_PROVIDER -> DeleteTargetMode.PERMANENT_ONLY
            }
        }
        return modes.singleOrNull() ?: DeleteTargetMode.MIXED
    }

    fun targetMode(path: Path): DeleteTargetMode = targetModeForBackends(listOf(backend(path)))

    fun targetMode(paths: List<Path>): DeleteTargetMode =
        targetModeForBackends(paths.map(::backend))

    fun supportsPermanentDelete(path: Path): Boolean =
        targetMode(path) == DeleteTargetMode.LOCAL_TRASH

    fun supportsPermanentDelete(paths: List<Path>): Boolean =
        targetMode(paths) == DeleteTargetMode.LOCAL_TRASH

    fun supportsSecureShred(path: Path): Boolean {
        if (backend(path) != DeleteBackend.LOCAL) {
            return false
        }
        return runCatching {
            Files.exists(path) && Files.isWritable(path) &&
                (Files.isRegularFile(path) || Files.isDirectory(path))
        }.getOrDefault(false)
    }

    fun supportsSecureShred(paths: List<Path>): Boolean =
        paths.isNotEmpty() && paths.all(::supportsSecureShred)
}

internal object DeleteOperationStore {
    private val directory: File
        get() = File(application.noBackupFilesDir, "delete-operations").apply { mkdirs() }

    @Synchronized
    fun save(operationId: String, options: DeleteOptions) {
        val target = File(directory, "$operationId.json")
        val temporary = File(directory, "$operationId.json.tmp")
        val persisted = options.copy(skipConfirmationForSession = false)
        temporary.writeText(
            JSONObject()
                .put("permanentDelete", persisted.permanentDelete)
                .put("secureShred", persisted.secureShred)
                .toString()
        )
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Unable to persist delete operation")
        }
    }

    @Synchronized
    fun load(operationId: String): DeleteOptions? {
        val file = File(directory, "$operationId.json")
        if (!file.isFile) return null
        return runCatching {
            val json = JSONObject(file.readText())
            DeleteOptions(
                permanentDelete = json.optBoolean("permanentDelete"),
                skipConfirmationForSession = false,
                secureShred = json.optBoolean("secureShred")
            )
        }.getOrNull()
    }

    @Synchronized
    fun delete(operationId: String) {
        File(directory, "$operationId.json").delete()
        File(directory, "$operationId.json.tmp").delete()
    }
}

internal object DeleteTargetPolicy {
    fun shouldUseLocalRecycleBin(
        path: Path,
        options: DeleteOptions,
        recycleBinEnabled: Boolean
    ): Boolean =
        recycleBinEnabled &&
            !options.directDelete &&
            DeleteOptionsSupport.targetMode(path) == DeleteTargetMode.LOCAL_TRASH
}

object DeleteConfirmationSessionStore {
    private var targetMode: DeleteTargetMode? = null
    private var options: DeleteOptions? = null

    fun get(targetMode: DeleteTargetMode): DeleteOptions? =
        options.takeIf { this.targetMode == targetMode }

    fun update(targetMode: DeleteTargetMode, options: DeleteOptions) {
        if (options.skipConfirmationForSession) {
            this.targetMode = targetMode
            this.options = options
        } else {
            clear()
        }
    }

    fun clear() {
        targetMode = null
        options = null
    }
}
