package com.wisso.wizefiles.feature.filebrowser

import android.content.Context
import android.os.Build
import java.nio.file.Path
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.core.imageloader.coil.AndroidPackageArchiveKind
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.getBrokenSymbolicLinkName
import com.wisso.wizefiles.core.files.mime.getName
import com.wisso.wizefiles.core.files.mime.isApk
import com.wisso.wizefiles.core.files.mime.isImage
import com.wisso.wizefiles.core.files.mime.isMedia
import com.wisso.wizefiles.core.files.mime.isPdf
import com.wisso.wizefiles.provider.archive.createArchiveRootPath
import com.wisso.wizefiles.provider.document.documentSupportsThumbnail
import com.wisso.wizefiles.provider.document.isDocumentPath
import com.wisso.wizefiles.provider.ftp.isFtpPath
import com.wisso.wizefiles.provider.os.isLinuxPath
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.util.asFileName
import com.wisso.wizefiles.util.isGetPackageArchiveInfoCompatible
import com.wisso.wizefiles.util.isMediaMetadataRetrieverCompatible
import com.wisso.wizefiles.util.valueCompat
import java.text.CollationKey
import com.wisso.wizefiles.storage.FileMetadata
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull

val FileItem.name: String
    get() = path.name

val FileItem.baseName: String
    get() = if (attributes.isDirectory) name else name.asFileName().baseName

val FileItem.extension: String
    get() = if (attributes.isDirectory) "" else name.asFileName().extensions

fun FileItem.getMimeTypeName(context: Context): String {
        if (attributesNoFollowLinks.isSymbolicLink && isSymbolicLinkBroken) {
            return MimeType.getBrokenSymbolicLinkName(context)
        }
        return mimeType.getName(extension, context)
    }

val FileItem.isArchiveFile: Boolean
    get() = path.toLegacyPathOrNull()?.isArchiveFile(mimeType) == true

val FileItem.isListable: Boolean
    get() = attributes.isDirectory || isArchiveFile

val FileItem.listablePath: Path
    get() {
        val legacyPath = checkNotNull(path.toLegacyPathOrNull())
        return if (isArchiveFile) legacyPath.createArchiveRootPath() else legacyPath
    }

// @see PathAttributesFetcher.fetch
val FileItem.supportsThumbnail: Boolean
    get() {
        if (attributes.isDirectory) {
            return false
        }
        val packageContainerKind = AndroidPackageArchiveKind.fromPath(path.rawPath)
        val isPreviewableType = packageContainerKind != null ||
            mimeType.isApk || mimeType.isImage || mimeType.isMedia || mimeType.isPdf
        if (!isPreviewableType) {
            return false
        }
        val legacyPath = path.toLegacyPathOrNull() ?: return false
        if (legacyPath.isDocumentPath && attributes.documentSupportsThumbnail) {
            return true
        }
        if (legacyPath.isRemotePath) {
            val shouldReadRemotePath = !legacyPath.isFtpPath
                && Settings.READ_REMOTE_FILES_FOR_THUMBNAIL.valueCompat
            if (!shouldReadRemotePath) {
                return false
            }
        }
        return when {
            packageContainerKind != null -> true
            mimeType.isApk && path.isGetPackageArchiveInfoCompatible -> true
            mimeType.isImage -> true
            mimeType.isMedia && path.isMediaMetadataRetrieverCompatible -> true
            mimeType.isPdf && (legacyPath.isLinuxPath || legacyPath.isDocumentPath) ->
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    || Settings.SHOW_PDF_THUMBNAIL_PRE_28.valueCompat
            else -> false
        }
    }

// @see android.content.pm.parsing.ApkLiteParseUtils.parsePackageSplitNames
// @see android.content.pm.parsing.ParsingPackageUtils.validateName
// @see com.android.server.pm.PackageManagerService.getNextCodePath
private const val PACKAGE_NAME_COMPONENT_PATTERN = "[A-Za-z][0-9A-Z_a-z]*"
private const val PACKAGE_NAME_PATTERN =
    "$PACKAGE_NAME_COMPONENT_PATTERN(?:\\.$PACKAGE_NAME_COMPONENT_PATTERN)+"
private const val BASE64_URL_SAFE_CHARACTER_CLASS = "[0-9A-Za-z\\-_]"
private const val BASE64_URL_SAFE_PATTERN = ("(?:$BASE64_URL_SAFE_CHARACTER_CLASS{4})*"
    + "(?:$BASE64_URL_SAFE_CHARACTER_CLASS{3}=|$BASE64_URL_SAFE_CHARACTER_CLASS{2}==)?")
private val APP_DIRECTORY_REGEX =
    Regex("($PACKAGE_NAME_PATTERN)(?:-$BASE64_URL_SAFE_PATTERN)?")

val FileItem.appDirectoryPackageName: String?
    get() {
        if (!attributes.isDirectory) {
            return null
        }
        return APP_DIRECTORY_REGEX.matchEntire(name)?.groupValues?.get(1)
    }

fun FileItem.createDummyArchiveRoot(): FileItem =
    FileItem(
        checkNotNull(path.toLegacyPathOrNull()).createArchiveRootPath().toAppPath(), DummyCollationKey(), DummyArchiveRootFileMetadata,
        null, null, false, MimeType.DIRECTORY
    )

// Dummy collation key only to be added to the selection set, which may be used to determine file
// type when confirming deletion.
private class DummyCollationKey : CollationKey("") {
    override fun compareTo(other: CollationKey?): Int {
        throw UnsupportedOperationException()
    }

    override fun toByteArray(): ByteArray {
        throw UnsupportedOperationException()
    }
}

// Dummy attributes only to be added to the selection set, which may be used to determine file
// type when confirming deletion.
private val DummyArchiveRootFileMetadata = FileMetadata(
    isDirectory = true,
    sizeBytes = null,
    lastModifiedEpochMillis = null,
    isSymbolicLink = false
)
