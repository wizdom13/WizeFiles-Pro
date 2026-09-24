package com.wisso.wizefiles.feature.details.permission

import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.provider.common.PosixFileAttributes
import com.wisso.wizefiles.provider.common.readAttributes
import com.wisso.wizefiles.storage.FileMetadata
import com.wisso.wizefiles.storage.path.LocalAppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import java.text.Collator
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FilePropertiesPermissionTabFragmentTest {
    @Test
    fun isAvailableReturnsFalseWhenPathAttributesCannotBeRead() {
        val file = createTempDirectory().resolve("missing.txt").toFile()
        val fileItem = FileItem(
            path = LocalAppPath(file),
            nameCollationKey = Collator.getInstance().getCollationKey(file.name),
            attributesNoFollowLinks = FileMetadata(
                isDirectory = false,
                sizeBytes = null,
                lastModifiedEpochMillis = null
            ),
            symbolicLinkTarget = null,
            symbolicLinkTargetAttributes = null,
            isHidden = false,
            mimeType = MimeType.GENERIC
        )

        assertFalse(FilePropertiesPermissionTabFragment.isAvailable(fileItem))
    }

    @Test
    fun isAvailableMatchesReachablePosixAttributes() {
        val file = createTempDirectory().resolve("present.txt").toFile().apply { writeText("x") }
        val fileItem = FileItem(
            path = LocalAppPath(file),
            nameCollationKey = Collator.getInstance().getCollationKey(file.name),
            attributesNoFollowLinks = FileMetadata(
                isDirectory = false,
                sizeBytes = file.length(),
                lastModifiedEpochMillis = file.lastModified()
            ),
            symbolicLinkTarget = null,
            symbolicLinkTargetAttributes = null,
            isHidden = false,
            mimeType = MimeType.GENERIC
        )

        val expected = fileItem.path.toLegacyPathOrNull()
            ?.let { path -> runCatching { path.readAttributes(PosixFileAttributes::class.java) }.getOrNull() }
            ?.let { attributes ->
                attributes.owner() != null || attributes.group() != null || attributes.mode() != null
                    || attributes.seLinuxContext() != null
            }
            ?: false

        assertEquals(expected, FilePropertiesPermissionTabFragment.isAvailable(fileItem))
    }
}
