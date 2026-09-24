package com.wisso.wizefiles.feature.filebrowser

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.clipboardManager
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.isApk
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.core.files.provider.legacy.fileProviderUri
import com.wisso.wizefiles.feature.filejobs.FileOperationService
import com.wisso.wizefiles.feature.internalviewer.InternalOpenIntents
import com.wisso.wizefiles.feature.internalviewer.InternalOpenPolicy
import com.wisso.wizefiles.feature.packageinstaller.AndroidPackageInstallerInput
import com.wisso.wizefiles.feature.packageinstaller.PackageInstallerActivity
import com.wisso.wizefiles.navigation.BookmarkDirectories
import com.wisso.wizefiles.navigation.BookmarkDirectory
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.provider.os.isLinuxPath
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.terminal.TerminalLauncher
import com.wisso.wizefiles.util.Success
import com.wisso.wizefiles.util.copyText
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.createSendStreamIntent
import com.wisso.wizefiles.util.createViewIntent
import com.wisso.wizefiles.util.extraPath
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.showToast
import com.wisso.wizefiles.util.startActivitySafe
import com.wisso.wizefiles.util.valueCompat
import com.wisso.wizefiles.util.withChooser
import java.nio.file.Path

internal class FileListExternalActionController(
    private val viewModel: FileListViewModel
) {
    private var context: Context? = null
    private var pickFiles: ((FileItemSet) -> Unit)? = null
    private var navigateTo: ((Path) -> Unit)? = null
    private var confirmReplace: ((FileItem) -> Unit)? = null
    private var showOpenApkDialog: ((FileItem) -> Unit)? = null

    fun bind(
        context: Context,
        pickFiles: (FileItemSet) -> Unit,
        navigateTo: (Path) -> Unit,
        confirmReplace: (FileItem) -> Unit,
        showOpenApkDialog: (FileItem) -> Unit
    ) {
        release()
        this.context = context
        this.pickFiles = pickFiles
        this.navigateTo = navigateTo
        this.confirmReplace = confirmReplace
        this.showOpenApkDialog = showOpenApkDialog
    }

    fun release() {
        showOpenApkDialog = null
        confirmReplace = null
        navigateTo = null
        pickFiles = null
        context = null
    }

    fun open(file: FileItem) {
        val pickOptions = viewModel.pickOptions
        if (pickOptions != null) {
            openForPicker(file, pickOptions)
            return
        }
        if (file.mimeType.isApk || AndroidPackageInstallerInput.extension(file.name) != null) {
            openApk(file)
            return
        }
        val context = requireNotNull(context)
        val legacyPath = file.path.toLegacyPathOrNull()
        val internalTarget = InternalOpenPolicy.targetAfterExtraction(file.mimeType, file.path.name)
        if (internalTarget == InternalOpenPolicy.Target.WEB_DOCUMENT_VIEWER) {
            if (legacyPath?.isArchivePath == true) {
                FileOperationService.openInternalViewer(legacyPath, file.mimeType, context)
                return
            }
            InternalOpenIntents.create(context, file, listOf(file))?.let {
                context.startActivitySafe(it)
                return
            }
        }
        if (file.isListable) {
            navigateTo?.invoke(file.listablePath)
            return
        }
        if (
            legacyPath?.isArchivePath == true &&
            internalTarget != InternalOpenPolicy.Target.EXTERNAL_APP
        ) {
            FileOperationService.openInternalViewer(legacyPath, file.mimeType, context)
            return
        }
        val fileListState = viewModel.fileListStateful
        val siblings = if (fileListState is Success) fileListState.value else listOf(file)
        InternalOpenIntents.create(context, file, siblings)?.let {
            context.startActivitySafe(it)
            return
        }
        openWithIntent(file, withChooser = false)
    }

    fun installApk(file: FileItem) {
        val context = requireNotNull(context)
        val legacyPath = file.path.toLegacyPathOrNull() ?: return
        if (legacyPath.isArchivePath) {
            FileOperationService.installApk(legacyPath, context)
            return
        }
        context.startActivitySafe(
            PackageInstallerActivity.createIntent(context, legacyPath.fileProviderUri, file.name)
        )
    }

    fun viewApk(file: FileItem) {
        navigateTo?.invoke(file.listablePath)
    }

    fun openWith(file: FileItem) {
        openWithIntent(file, withChooser = true)
    }

    fun hasEntryWithName(name: String): Boolean = entryWithName(name) != null

    fun entryWithName(name: String): FileItem? {
        val fileListData = viewModel.fileListStateful
        return if (fileListData is Success) {
            fileListData.value.find { it.name == name }
        } else {
            null
        }
    }

    fun rename(file: FileItem, newName: String) {
        file.path.toLegacyPathOrNull()?.let {
            FileOperationService.rename(it, newName, requireNotNull(context))
        }
        viewModel.selectFile(file, false)
    }

    fun shareSelection(files: FileItemSet) {
        share(
            files.mapNotNull { it.path.toLegacyPathOrNull() },
            files.map { it.mimeType }
        )
        viewModel.selectFiles(files, false)
    }

    fun share(path: Path, mimeType: MimeType) {
        share(listOf(path), listOf(mimeType))
    }

    fun copyPath(path: Path) {
        clipboardManager.copyText(path.toUserFriendlyString(), requireNotNull(context))
    }

    fun openInTerminal(path: Path) {
        if (path.isLinuxPath) {
            TerminalLauncher.open(path.toFile().path, requireNotNull(context))
        }
    }

    fun addBookmark(path: AppPath) {
        val context = requireNotNull(context)
        val persisted = BookmarkDirectories.add(BookmarkDirectory(null, path))
        context.showToast(if (persisted) R.string.file_add_bookmark_success else R.string.error)
    }

    fun createShortcut(path: Path, mimeType: MimeType) {
        val context = requireNotNull(context)
        val isDirectory = mimeType == MimeType.DIRECTORY
        val shortcutInfo = ShortcutInfoCompat.Builder(context, path.toString())
            .setShortLabel(path.name)
            .setIntent(
                if (isDirectory) {
                    FileListActivity.createViewIntent(path.toAppPath())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                } else {
                    OpenFileActivity.createIntent(path, mimeType)
                }
            )
            .setIcon(
                IconCompat.createWithResource(
                    context,
                    if (isDirectory) R.mipmap.ic_shortcut_directory else R.mipmap.ic_shortcut_file
                )
            )
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            context.showToast(R.string.shortcut_created)
        }
    }

    fun create(name: String, directory: Boolean) {
        val path = viewModel.currentPath.resolve(name)
        FileOperationService.create(path, directory, requireNotNull(context))
    }

    private fun openForPicker(file: FileItem, pickOptions: PickOptions) {
        if (file.attributes.isDirectory) {
            if (
                pickOptions.mode == PickOptions.Mode.OPEN_FILE &&
                pickOptions.allowDirectories &&
                !pickOptions.selectWithLongPress
            ) {
                pickFiles?.invoke(fileItemSetOf(file))
            } else {
                file.path.toLegacyPathOrNull()?.let { navigateTo?.invoke(it) }
            }
        } else if (!pickOptions.selectWithLongPress) {
            when (pickOptions.mode) {
                PickOptions.Mode.OPEN_FILE -> pickFiles?.invoke(fileItemSetOf(file))
                PickOptions.Mode.CREATE_FILE -> confirmReplace?.invoke(file)
                PickOptions.Mode.OPEN_DIRECTORY -> Unit
            }
        }
    }

    private fun openApk(file: FileItem) {
        if (!file.isListable) {
            installApk(file)
            return
        }
        when (Settings.OPEN_APK_DEFAULT_ACTION.valueCompat) {
            OpenApkDefaultAction.INSTALL -> installApk(file)
            OpenApkDefaultAction.VIEW -> viewApk(file)
            OpenApkDefaultAction.ASK -> showOpenApkDialog?.invoke(file)
        }
    }

    private fun openWithIntent(file: FileItem, withChooser: Boolean) {
        val context = requireNotNull(context)
        val path = file.path
        val legacyPath = path.toLegacyPathOrNull() ?: return
        val mimeType = file.mimeType
        if (legacyPath.isArchivePath) {
            FileOperationService.open(legacyPath, mimeType, withChooser, context)
            return
        }
        val intent = legacyPath.fileProviderUri.createViewIntent(mimeType)
            .apply { extraPath = path }
            .let {
                if (withChooser) {
                    it.withChooser(
                        EditFileActivity::class.createIntent()
                            .putArgs(EditFileActivity.Args(legacyPath.toAppPath(), mimeType)),
                        OpenFileAsDialogActivity::class.createIntent()
                            .putArgs(OpenFileAsDialogFragment.Args(legacyPath.toAppPath()))
                    )
                } else {
                    it
                }
            }
        context.startActivitySafe(intent)
    }

    private fun share(paths: List<Path>, mimeTypes: List<MimeType>) {
        val intent = paths.map { it.fileProviderUri }
            .createSendStreamIntent(mimeTypes)
            .withChooser()
        requireNotNull(context).startActivitySafe(intent)
    }
}
