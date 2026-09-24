package com.wisso.wizefiles.feature.filebrowser

import android.content.ClipData
import android.content.Context
import android.content.Intent
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.asMimeTypeOrNull
import com.wisso.wizefiles.core.files.mime.extension
import com.wisso.wizefiles.core.files.provider.legacy.fileProviderUri
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.asFileNameOrNull
import com.wisso.wizefiles.util.create
import com.wisso.wizefiles.util.extraPath
import com.wisso.wizefiles.util.extraPathList
import com.wisso.wizefiles.util.takeIfNotEmpty

internal object FileListPickerCoordinator {
    fun resolveOptions(intent: Intent): PickOptions? = when (val action = intent.action) {
        Intent.ACTION_GET_CONTENT,
        Intent.ACTION_OPEN_DOCUMENT,
        Intent.ACTION_CREATE_DOCUMENT -> {
            val mode = if (action == Intent.ACTION_CREATE_DOCUMENT) {
                PickOptions.Mode.CREATE_FILE
            } else {
                PickOptions.Mode.OPEN_FILE
            }
            val mimeType = intent.type?.asMimeTypeOrNull() ?: MimeType.ANY
            val fileName = if (mode == PickOptions.Mode.CREATE_FILE) {
                intent.getStringExtra(Intent.EXTRA_TITLE)?.asFileNameOrNull()?.value
                    ?: mimeType.extension?.let { "file.$it" } ?: "file"
            } else {
                null
            }
            val readOnly = action == Intent.ACTION_GET_CONTENT
            val extraMimeTypes = if (mode == PickOptions.Mode.OPEN_FILE) {
                intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
                    ?.mapNotNull { it.asMimeTypeOrNull() }
                    ?.takeIfNotEmpty()
            } else {
                null
            }
            val mimeTypes = extraMimeTypes ?: listOf(mimeType)
            val localOnly = intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false)
            val allowMultiple = mode != PickOptions.Mode.CREATE_FILE &&
                intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            val allowDirectories = mode == PickOptions.Mode.OPEN_FILE &&
                intent.getBooleanExtra(FileListActivity.EXTRA_ALLOW_PICK_DIRECTORIES, false)
            val selectWithLongPress = mode == PickOptions.Mode.OPEN_FILE &&
                intent.getBooleanExtra(FileListActivity.EXTRA_PICK_SELECT_WITH_LONG_PRESS, false)
            PickOptions(
                mode,
                fileName,
                readOnly,
                mimeTypes,
                localOnly,
                allowMultiple,
                allowDirectories,
                selectWithLongPress
            )
        }
        Intent.ACTION_OPEN_DOCUMENT_TREE -> PickOptions(
            PickOptions.Mode.OPEN_DIRECTORY,
            null,
            false,
            emptyList(),
            intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false),
            false
        )
        else -> null
    }

    fun resolveTitle(context: Context, intent: Intent, options: PickOptions?): CharSequence {
        val pickOptions = options ?: return context.getString(R.string.file_list_title)
        return when (pickOptions.mode) {
            PickOptions.Mode.OPEN_FILE -> context.resources.getQuantityString(
                R.plurals.file_list_title_open_file,
                if (pickOptions.allowMultiple) Int.MAX_VALUE else 1
            )
            PickOptions.Mode.CREATE_FILE -> context.getString(R.string.file_list_title_create_file)
            PickOptions.Mode.OPEN_DIRECTORY -> FileListActivity.resolveOpenDirectoryTitle(
                intent.getStringExtra(FileListActivity.EXTRA_OPEN_DIRECTORY_TITLE),
                context.getString(R.string.file_list_choose_directory)
            )
        }
    }

    fun resolveDirectoryConfirmationLabel(context: Context, intent: Intent): CharSequence =
        FileListActivity.resolveOpenDirectoryConfirmationLabel(
            intent.getStringExtra(FileListActivity.EXTRA_OPEN_DIRECTORY_CONFIRMATION_LABEL),
            context.getString(R.string.file_list_use_current_directory)
        )

    fun createResultIntent(paths: LinkedHashSet<AppPath>, options: PickOptions): Intent =
        Intent().apply {
            if (paths.size == 1) {
                val path = paths.single()
                if (shouldAttachDataUri(options.mode)) {
                    data = path.toLegacyPathOrNull()?.fileProviderUri
                }
                extraPath = path
            } else {
                val mimeTypes = options.mimeTypes.map { it.value }
                val items = paths.mapNotNull { it.toLegacyPathOrNull()?.fileProviderUri }
                    .map { ClipData.Item(it) }
                clipData = ClipData::class.create(null, mimeTypes, items)
                extraPathList = paths.toList()
            }
            addFlags(buildResultFlags(options))
        }

    fun resolveOpenDirectoryResult(options: PickOptions?, currentPath: AppPath?): AppPath? =
        currentPath?.takeIf { options?.mode == PickOptions.Mode.OPEN_DIRECTORY }

    fun shouldAttachDataUri(mode: PickOptions.Mode): Boolean =
        mode != PickOptions.Mode.OPEN_DIRECTORY

    fun buildResultFlags(options: PickOptions): Int {
        var flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        if (!options.readOnly) {
            flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }
        if (options.mode == PickOptions.Mode.OPEN_DIRECTORY) {
            flags = flags or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        }
        return flags
    }
}
