package com.wisso.wizefiles.feature.filebrowser

import com.wisso.wizefiles.core.files.model.FileItem

internal data class FileListSubtitleCounts(val directories: Int, val files: Int) {
    companion object {
        fun from(items: List<FileItem>): FileListSubtitleCounts {
            val directories = items.count { it.attributes.isDirectory }
            return FileListSubtitleCounts(directories, items.size - directories)
        }
    }
}
