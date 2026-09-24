package com.wisso.wizefiles.core.files.model

import android.os.Parcelable
import androidx.annotation.WorkerThread
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.asMimeType
import com.wisso.wizefiles.storage.FileMetadata
import com.wisso.wizefiles.storage.legacy.detectLocalMimeType
import com.wisso.wizefiles.storage.legacy.readFileItemSnapshot
import com.wisso.wizefiles.storage.local.LocalFileNode
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.LocalAppPath
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import com.wisso.wizefiles.feature.filebrowser.getCollationKeyForFileName
import com.wisso.wizefiles.util.ParcelableParceler
import java.io.IOException
import java.text.CollationKey
import java.text.Collator

enum class PendingFileOperationState {
    QUEUED,
    COPYING
}

@Parcelize
data class FileItem(
    val path: @WriteWith<ParcelableParceler> AppPath,
    val nameCollationKey: @WriteWith<ParcelableParceler> CollationKey,
    val attributesNoFollowLinks: @WriteWith<FileMetadataParceler> FileMetadata,
    val symbolicLinkTarget: String?,
    private val symbolicLinkTargetAttributes: @WriteWith<NullableFileMetadataParceler> FileMetadata?,
    val isHidden: Boolean,
    val mimeType: MimeType,
    val pendingOperationState: PendingFileOperationState? = null,
    val directoryItemCount: Int? = null
) : Parcelable {
    val attributes: FileMetadata
        get() = symbolicLinkTargetAttributes ?: attributesNoFollowLinks

    val isSymbolicLinkBroken: Boolean
        get() {
            check(attributesNoFollowLinks.isSymbolicLink) { "Not a symbolic link" }
            return symbolicLinkTargetAttributes == null
        }
}

@WorkerThread
@Throws(IOException::class)
fun AppPath.loadFileItem(): FileItem {
    val snapshot = readFileItemSnapshot()
    return FileItem(
        path = this,
        nameCollationKey = Collator.getInstance().getCollationKeyForFileName(name),
        attributesNoFollowLinks = snapshot.metadataNoFollowLinks,
        symbolicLinkTarget = snapshot.symbolicLinkTarget,
        symbolicLinkTargetAttributes = snapshot.symbolicLinkTargetMetadata,
        isHidden = snapshot.isHidden,
        mimeType = snapshot.mimeType
    )
}

fun LocalFileNode.toFileItem(metadata: FileMetadata): FileItem {
    val localPath = LocalAppPath(file)
    val mimeType = detectLocalMimeType(localPath, metadata).asMimeType()
    return FileItem(
        path = localPath,
        nameCollationKey = Collator.getInstance().getCollationKeyForFileName(name),
        attributesNoFollowLinks = metadata,
        symbolicLinkTarget = null,
        symbolicLinkTargetAttributes = null,
        isHidden = file.isHidden,
        mimeType = mimeType
    )
}
