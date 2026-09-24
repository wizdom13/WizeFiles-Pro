package com.wisso.wizefiles.feature.filebrowser

import java.nio.file.Path
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.isSupportedArchive
import com.wisso.wizefiles.provider.archive.ArchiveDisplayNameRegistry
import com.wisso.wizefiles.provider.archive.archiveFile
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.provider.document.isDocumentPath
import com.wisso.wizefiles.provider.document.resolver.DocumentResolver
import com.wisso.wizefiles.provider.os.isLinuxPath

val Path.name: String
    get() = fileName?.toString()
        ?: if (isArchivePath) ArchiveDisplayNameRegistry.get(this) ?: archiveFile.name else "/"

fun Path.toUserFriendlyString(): String = if (isLinuxPath) toFile().path else toUri().toString()

fun Path.isArchiveFile(mimeType: MimeType): Boolean {
    if (isArchivePath) {
        return false
    }
    return mimeType.isSupportedArchive || name.isSupportedArchiveFileName()
}

internal fun String.isSupportedArchiveFileName(): Boolean {
    val lowerCaseName = lowercase()
    return SUPPORTED_ARCHIVE_FILE_EXTENSIONS.any(lowerCaseName::endsWith)
        || lowerCaseName.matches(RAR_MULTIPART_EXTENSION_REGEX)
        || lowerCaseName.matches(RAR_PART_EXTENSION_REGEX)
        || lowerCaseName.matches(SEVEN_ZIP_MULTIPART_EXTENSION_REGEX)
        || lowerCaseName.matches(ZIP_MULTIPART_EXTENSION_REGEX)
}

internal val SUPPORTED_ARCHIVE_FILE_EXTENSIONS = setOf(
    ".7z", ".apk", ".arj", ".bz2", ".cab", ".chm", ".cpio", ".deb", ".dmg", ".esd",
    ".ext2", ".ext3", ".ext4", ".fat", ".fat12", ".fat16", ".fat32", ".gz", ".hfs",
    ".hfsx", ".hex", ".ihex", ".img", ".iso", ".jar", ".lha", ".lzh", ".lz4", ".lzma",
    ".msi", ".msp", ".mst", ".nsi", ".nsis", ".ntfs", ".qcow", ".qcow2", ".qcow3",
    ".rar", ".rpm", ".sfs", ".squashfs", ".swm", ".tar", ".tbz", ".tbz2", ".tgz",
    ".txz", ".udf", ".vdi", ".vhd", ".vhdx", ".vmdk", ".war", ".wim", ".xar", ".xz",
    ".z", ".zip", ".zipx", ".zst"
)

internal val RAR_MULTIPART_EXTENSION_REGEX = Regex(".*\\.r\\d{2}$")
internal val RAR_PART_EXTENSION_REGEX = Regex(".*\\.part\\d+\\.rar$")
internal val SEVEN_ZIP_MULTIPART_EXTENSION_REGEX = Regex(".*\\.7z\\.\\d{3,}$")
internal val ZIP_MULTIPART_EXTENSION_REGEX = Regex(".*\\.zip\\.\\d{3,}$")

val Path.isLocalPath: Boolean
    get() =
        isLinuxPath || (isDocumentPath && DocumentResolver.isLocal(this as DocumentResolver.Path))

val Path.isRemotePath: Boolean
    get() = !isLocalPath
