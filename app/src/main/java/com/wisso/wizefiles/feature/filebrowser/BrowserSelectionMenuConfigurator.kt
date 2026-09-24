// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.view.Menu
import androidx.core.view.isVisible
import com.wisso.wizefiles.R
import com.wisso.wizefiles.batchrename.BatchRenameAvailability
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.core.files.mime.isApk
import com.wisso.wizefiles.feature.internalviewer.InternalOpenPolicy
import com.wisso.wizefiles.viewer.text.isTextEditorSupported
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import java.nio.file.Path

internal object BrowserSelectionMenuConfigurator {
    fun configure(
        menu: Menu,
        files: FileItemSet,
        pickOptions: PickOptions?,
        currentPath: Path,
        inRecycleBin: Boolean,
        otherPanePath: Path?,
        isWritableLocation: (Path) -> Boolean,
        isMutableFile: (FileItem) -> Boolean
    ) {
        if (pickOptions != null) {
            val isOpen = when (pickOptions.mode) {
                PickOptions.Mode.OPEN_FILE, PickOptions.Mode.OPEN_DIRECTORY -> true
                PickOptions.Mode.CREATE_FILE -> false
            }
            menu.findItem(R.id.action_open).isVisible = isOpen
            menu.findItem(R.id.action_create).isVisible = !isOpen
            menu.findItem(R.id.action_select_all).isVisible = pickOptions.allowMultiple
            return
        }

        val singleFile = files.singleOrNull()
        val containsDirectory = files.any { it.attributes.isDirectory }
        val singleApk = singleFile?.mimeType?.isApk == true &&
            singleFile.attributes.isDirectory.not() && !inRecycleBin
        menu.findItem(R.id.action_sign_apk).isVisible = singleApk
        menu.findItem(R.id.action_verify_apk).isVisible = singleApk
        setPackageVisibility(menu, singleFile, inRecycleBin, ".aab",
            R.id.action_sign_aab, R.id.action_verify_aab)
        setPackageVisibility(menu, singleFile, inRecycleBin, ".apks",
            R.id.action_sign_apks, R.id.action_verify_apks)
        setPackageVisibility(menu, singleFile, inRecycleBin, ".xapk",
            R.id.action_sign_xapk, R.id.action_verify_xapk)
        val singleApkm = singleFile != null &&
            singleFile.path.name.endsWith(".apkm", ignoreCase = true) &&
            !singleFile.attributes.isDirectory && !inRecycleBin
        menu.findItem(R.id.action_import_apkm).isVisible = singleApkm

        val singleLegacyPath = singleFile?.path?.toLegacyPathOrNull()
        menu.findItem(R.id.action_open_with).isVisible =
            singleFile != null && FileItemMenuVisibilityPolicy.shouldShowOpenWith(
                singleFile.attributes.isDirectory, inRecycleBin
            )
        menu.findItem(R.id.action_edit).isVisible =
            singleFile != null &&
                !singleFile.attributes.isDirectory &&
                !inRecycleBin &&
                singleFile.mimeType.isTextEditorSupported &&
                isMutableFile(singleFile)
        val singleAudio = singleFile != null &&
            !singleFile.attributes.isDirectory &&
            !inRecycleBin &&
            InternalOpenPolicy.targetAfterExtraction(singleFile.mimeType, singleFile.path.name) ==
                InternalOpenPolicy.Target.AUDIO_PLAYER
        menu.findItem(R.id.action_set_ringtone).isVisible = singleAudio
        menu.findItem(R.id.action_rename).isVisible =
            singleFile != null && !inRecycleBin && isMutableFile(singleFile)
        menu.findItem(R.id.action_copy_path).isVisible =
            singleFile != null && singleLegacyPath != null && !inRecycleBin
        menu.findItem(R.id.action_add_bookmark).isVisible =
            singleFile?.attributes?.isDirectory == true && !inRecycleBin
        menu.findItem(R.id.action_create_shortcut).isVisible =
            singleFile != null && singleLegacyPath != null && !inRecycleBin
        menu.findItem(R.id.action_properties).isVisible = singleFile != null

        menu.findItem(R.id.action_copy_to_other_pane).isVisible =
            otherPanePath?.let(isWritableLocation) == true
        menu.findItem(R.id.action_move_to_other_pane).isVisible =
            otherPanePath?.let(isWritableLocation) == true &&
                files.none { it.path.toLegacyPathOrNull()?.fileSystem?.isReadOnly == true }

        if (inRecycleBin) {
            menu.findItem(R.id.action_restore).isVisible = true
            menu.findItem(R.id.action_cut).isVisible = false
            menu.findItem(R.id.action_copy).isVisible = false
            menu.findItem(R.id.action_delete).isVisible = true
            menu.findItem(R.id.action_extract).isVisible = false
            menu.findItem(R.id.action_archive).isVisible = false
            menu.findItem(R.id.action_encrypt).isVisible = false
            menu.findItem(R.id.action_decrypt).isVisible = false
            menu.findItem(R.id.action_share).isVisible = false
            menu.findItem(R.id.action_send_nearby).isVisible = false
            menu.findItem(R.id.action_batch_rename).isVisible = false
            return
        }

        val isAnyFileReadOnly = files.any {
            it.path.toLegacyPathOrNull()?.fileSystem?.isReadOnly == true
        }
        menu.findItem(R.id.action_cut).isVisible = !isAnyFileReadOnly
        val areAllFilesArchivePaths = files.all {
            it.path.toLegacyPathOrNull()?.isArchivePath == true
        }
        menu.findItem(R.id.action_copy)
            .setIcon(
                if (areAllFilesArchivePaths) R.drawable.ic_extract_control_normal_24dp
                else R.drawable.ic_copy_control_normal_24dp
            )
            .setTitle(
                if (areAllFilesArchivePaths) R.string.file_list_select_action_extract
                else R.string.copy
            )
        menu.findItem(R.id.action_delete).isVisible = files.all(isMutableFile)
        menu.findItem(R.id.action_restore).isVisible = false
        menu.findItem(R.id.action_extract).isVisible = files.all { it.isArchiveFile }
        menu.findItem(R.id.action_archive).isVisible = !currentPath.fileSystem.isReadOnly
        val areAllFilesDecryptable = files.all {
            it.attributes.isDirectory || it.name.endsWith(".enc")
        }
        menu.findItem(R.id.action_encrypt).isVisible = !isAnyFileReadOnly
        menu.findItem(R.id.action_decrypt).isVisible =
            !isAnyFileReadOnly && areAllFilesDecryptable
        menu.findItem(R.id.action_share).isVisible = !containsDirectory
        menu.findItem(R.id.action_batch_rename).isVisible = BatchRenameAvailability.isAvailable(
            selectedCount = files.size,
            containsDirectory = containsDirectory,
            containsUnsupportedPath = files.any { it.path.toLegacyPathOrNull() == null },
            isReadOnly = isAnyFileReadOnly || currentPath.fileSystem.isReadOnly,
            containsArchivePath = files.any {
                it.path.toLegacyPathOrNull()?.isArchivePath == true
            },
            isInRecycleBin = false
        )
    }

    private fun setPackageVisibility(
        menu: Menu,
        file: FileItem?,
        inRecycleBin: Boolean,
        extension: String,
        signAction: Int,
        verifyAction: Int
    ) {
        val visible = file != null &&
            file.path.name.endsWith(extension, ignoreCase = true) &&
            !file.attributes.isDirectory && !inRecycleBin
        menu.findItem(signAction).isVisible = visible
        menu.findItem(verifyAction).isVisible = visible
    }
}
