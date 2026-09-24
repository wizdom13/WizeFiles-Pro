// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.View
import android.widget.EditText
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.settings.SettingsActivity
import com.wisso.wizefiles.settings.SettingsBackupViewIntent
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.extraPath
import com.wisso.wizefiles.util.extraPathList
import java.nio.file.Path

internal object BrowserKeyboardInput {
    fun shortcutCommand(keyCode: Int, event: KeyEvent): BrowserCommand? = when {
        event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_C -> BrowserCommand.COPY
        event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_X -> BrowserCommand.CUT
        event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_V -> BrowserCommand.PASTE
        else -> null
    }

    fun keyUpCommand(keyCode: Int): BrowserCommand? = when (keyCode) {
        KeyEvent.KEYCODE_FORWARD_DEL -> BrowserCommand.DELETE
        KeyEvent.KEYCODE_F2 -> BrowserCommand.RENAME
        else -> null
    }

    fun isEditableInputFocused(focused: View?): Boolean =
        focused is EditText || focused?.onCheckIsTextEditor() == true

    fun shortcutGroup(context: Context): KeyboardShortcutGroup = KeyboardShortcutGroup(
        context.getString(R.string.file_list_keyboard_shortcuts_group),
        listOf(
            KeyboardShortcutInfo(context.getString(R.string.copy), KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON),
            KeyboardShortcutInfo(context.getString(R.string.cut), KeyEvent.KEYCODE_X, KeyEvent.META_CTRL_ON),
            KeyboardShortcutInfo(context.getString(R.string.paste), KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON),
            KeyboardShortcutInfo(context.getString(R.string.delete), KeyEvent.KEYCODE_FORWARD_DEL, 0),
            KeyboardShortcutInfo(context.getString(R.string.rename), KeyEvent.KEYCODE_F2, 0)
        )
    )
}

internal object FileListLaunchRouting {
    fun shouldShowExitConfirmation(
        isAtTopOfCurrentStorage: Boolean,
        isPickFlow: Boolean
    ): Boolean = isAtTopOfCurrentStorage && !isPickFlow

    fun shouldFinishAfterExitConfirmation(confirmed: Boolean): Boolean = confirmed

    fun supportsTabs(action: String?): Boolean = action !in setOf(
        Intent.ACTION_GET_CONTENT,
        Intent.ACTION_OPEN_DOCUMENT,
        Intent.ACTION_CREATE_DOCUMENT,
        Intent.ACTION_OPEN_DOCUMENT_TREE
    )

    fun findRestoreSettingsBackupUri(
        intent: Intent,
        resolveDisplayName: (Uri) -> String? = { null }
    ): Uri? = SettingsBackupViewIntent.findBackupUri(intent, resolveDisplayName)

    fun createRestoreSettingsIntent(sourceIntent: Intent, backupUri: Uri): Intent =
        SettingsActivity::class.createIntent()
            .setAction(Intent.ACTION_VIEW)
            .setData(backupUri)
            .addFlags(
                sourceIntent.flags and (
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                    )
            )

    fun createViewIntent(path: AppPath): Intent =
        FileListActivity::class.createIntent()
            .setAction(Intent.ACTION_VIEW)
            .apply { extraPath = path }

    fun normalizeOpenDirectoryInput(input: Any?): AppPath? = when (input) {
        null -> null
        is FileListActivity.OpenDirectoryRequest -> input.initialPath
        is AppPath -> input
        is Path -> input.toAppPath()
        else -> null
    }

    fun resolveOpenDirectoryTitle(customTitle: String?, fallbackTitle: String): String =
        customTitle?.takeIf { it.isNotBlank() } ?: fallbackTitle

    fun resolveOpenDirectoryConfirmationLabel(
        customLabel: String?,
        fallbackLabel: String
    ): String = customLabel?.takeIf { it.isNotBlank() } ?: fallbackLabel
}

internal object FileListContractIntents {
    fun openFile(context: Context, input: List<MimeType>): Intent =
        FileListActivity::class.createIntent()
            .setAction(Intent.ACTION_OPEN_DOCUMENT)
            .setType(MimeType.ANY.value)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_MIME_TYPES, input.map { it.value }.toTypedArray())

    fun openPath(context: Context, input: List<MimeType>): Intent =
        openFile(context, input)
            .putExtra(FileListActivity.EXTRA_ALLOW_PICK_DIRECTORIES, true)
            .putExtra(FileListActivity.EXTRA_PICK_SELECT_WITH_LONG_PRESS, true)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

    fun createFile(
        context: Context,
        input: Triple<MimeType, String?, AppPath?>
    ): Intent = FileListActivity::class.createIntent()
        .setAction(Intent.ACTION_CREATE_DOCUMENT)
        .setType(input.first.value)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .apply {
            input.second?.let { putExtra(Intent.EXTRA_TITLE, it) }
            input.third?.let { extraPath = it }
        }

    fun openDirectory(context: Context, input: Any?): Intent =
        FileListActivity::class.createIntent()
            .setAction(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .apply {
                FileListLaunchRouting.normalizeOpenDirectoryInput(input)?.let { extraPath = it }
                (input as? FileListActivity.OpenDirectoryRequest)?.let { request ->
                    request.title
                        ?.takeIf { it.isNotBlank() }
                        ?.let { putExtra(FileListActivity.EXTRA_OPEN_DIRECTORY_TITLE, it) }
                    request.confirmationLabel
                        ?.takeIf { it.isNotBlank() }
                        ?.let {
                            putExtra(FileListActivity.EXTRA_OPEN_DIRECTORY_CONFIRMATION_LABEL, it)
                        }
                }
            }

    fun parseSingle(resultCode: Int, intent: Intent?): AppPath? =
        if (resultCode == Activity.RESULT_OK) intent?.extraPath else null

    fun parseMultiple(resultCode: Int, intent: Intent?): List<AppPath> =
        if (resultCode == Activity.RESULT_OK) intent?.extraPathList ?: emptyList()
        else emptyList()
}
