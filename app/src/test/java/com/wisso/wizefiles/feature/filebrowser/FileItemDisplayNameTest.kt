package com.wisso.wizefiles.feature.filebrowser

import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.storage.FileMetadata
import com.wisso.wizefiles.storage.path.RawAppPath
import java.text.Collator
import org.junit.Assert.assertEquals
import org.junit.Test

class FileItemDisplayNameTest {

    @Test
    fun fileListNameUsesLeafNameForLocalFileUriPath() {
        val item = fileItem("file:///storage/emulated/0/Ads/180/", "180", true)

        assertEquals("180", item.name)
    }

    @Test
    fun cloudFileListNameDecodesUriEscapes() {
        val item = fileItem(
            "rclone://box/Travel/MR%20Wissam%20%26%20Raymond.pdf",
            "MR Wissam & Raymond.pdf",
            false
        )

        assertEquals("MR Wissam & Raymond.pdf", item.name)
    }

    private fun fileItem(rawPath: String, collationName: String, isDirectory: Boolean): FileItem =
        FileItem(
            path = RawAppPath(rawPath),
            nameCollationKey = Collator.getInstance().getCollationKey(collationName),
            attributesNoFollowLinks = FileMetadata(
                isDirectory = isDirectory,
                sizeBytes = if (isDirectory) null else 1L,
                lastModifiedEpochMillis = null
            ),
            symbolicLinkTarget = null,
            symbolicLinkTargetAttributes = null,
            isHidden = false,
            mimeType = if (isDirectory) MimeType.DIRECTORY else MimeType.PDF
        )
}
