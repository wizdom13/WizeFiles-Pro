package com.wisso.wizefiles.navigation

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.storage.StorageVolume
import androidx.annotation.DrawableRes
import androidx.annotation.Size
import androidx.annotation.StringRes
import java.nio.file.Path
import java.nio.file.Paths
import com.wisso.wizefiles.R
import com.wisso.wizefiles.feature.about.AboutActivity
import com.wisso.wizefiles.feature.appmanager.AppManagerActivity
import com.wisso.wizefiles.core.android.compat.EnvironmentCompat2
import com.wisso.wizefiles.core.android.compat.getDescriptionCompat
import com.wisso.wizefiles.core.android.compat.isPrimaryCompat
import com.wisso.wizefiles.core.android.compat.pathCompat
import com.wisso.wizefiles.core.files.model.JavaFile
import com.wisso.wizefiles.core.files.model.asFileSize
import com.wisso.wizefiles.provider.root.LibSuFileServiceLauncher
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.settings.SettingsActivity
import com.wisso.wizefiles.settings.StandardDirectoryListActivity
import com.wisso.wizefiles.recyclebin.RecycleBinManager
import com.wisso.wizefiles.storage.AddStorageDialogActivity
import com.wisso.wizefiles.storage.DeviceStorage
import com.wisso.wizefiles.storage.FileSystemRoot
import com.wisso.wizefiles.storage.PrimaryStorageVolume
import com.wisso.wizefiles.storage.Storage
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.storage.StorageVolumeListLiveData
import com.wisso.wizefiles.storage.VaultStorage
import com.wisso.wizefiles.vault.AddVaultDialogActivity
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.isMounted
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.supportsExternalStorageManager
import com.wisso.wizefiles.util.valueCompat

val navigationItems: List<NavigationItem?>
    get() =
        mutableListOf<NavigationItem?>().apply {
            addAll(builtInRootStorageItems)
            addAll(storageItems)
            if (Environment::class.supportsExternalStorageManager()) {
                // Starting with R, we can get read/write access to non-primary storage volumes with
                // MANAGE_EXTERNAL_STORAGE. However before R, we only have read-only access to them
                // and need to use the Storage Access Framework instead, so hide them in this case
                // to avoid confusion.
                addAll(storageVolumeItems)
            }
            add(AddStorageItem())
            addAll(vaultItems)
            add(AddVaultItem())
            val standardDirectoryItems = standardDirectoryItems
            if (standardDirectoryItems.isNotEmpty()) {
                add(null)
                addAll(standardDirectoryItems)
            }
            val bookmarkDirectoryItems = bookmarkDirectoryItems
            if (bookmarkDirectoryItems.isNotEmpty()) {
                add(null)
                addAll(bookmarkDirectoryItems)
            }
            add(null)
            add(transferCenterMenuItem)
            recycleBinItem?.let { add(it) }
            add(null)
            addAll(toolMenuItems)
            add(null)
            addAll(settingsMenuItems)
        }

private val storageItems: List<NavigationItem>
    @Size(min = 0)
    get() =
        Settings.STORAGES.value.orEmpty()
            .filter { it.isVisible && it !is VaultStorage && it.id !in builtInRootStorageIds }
            .map {
            if (it.path != null) PathStorageItem(it) else IntentStorageItem(it)
        }

private val builtInRootStorageIds: Set<Long> by lazy {
    setOf(FileSystemRoot(null, true).id, PrimaryStorageVolume(null, true).id)
}

private val builtInRootStorageItems: List<NavigationItem>
    @Size(min = 1)
    get() {
        val storagesById = Settings.STORAGES.value.orEmpty().associateBy { it.id }
        val builtInStorages = buildBuiltInRootStorages(
            storagesById = storagesById,
            isSuAvailable = LibSuFileServiceLauncher.isSuAvailable()
        )
        return builtInStorages.map { PathStorageItem(it) }
    }

internal fun buildBuiltInRootStorages(
    storagesById: Map<Long, Storage>,
    isSuAvailable: Boolean
): List<DeviceStorage> {
    val storages = mutableListOf<DeviceStorage>()
    if (isSuAvailable) {
        storages +=
            storagesById[FileSystemRoot(null, true).id] as? FileSystemRoot
                ?: FileSystemRoot(null, true)
    }
    storages +=
        storagesById[PrimaryStorageVolume(null, true).id] as? PrimaryStorageVolume
            ?: PrimaryStorageVolume(null, true)
    return storages.map { it.copy_(isVisible = true) }
}

private val vaultItems: List<NavigationItem>
    @Size(min = 0)
    get() =
        Settings.STORAGES.value.orEmpty().filterIsInstance<VaultStorage>().filter { it.isVisible }
            .map { IntentStorageItem(it) }

private abstract class PathItem(val path: Path) : NavigationItem() {
    override fun isChecked(listener: Listener): Boolean = listener.currentPath == path

    override fun onClick(listener: Listener) {
        if (this is NavigationRoot) {
            listener.navigateToRoot(path)
        } else {
            listener.navigateTo(path)
        }
        listener.closeNavigationDrawer()
    }
}

private class PathStorageItem(
    private val storage: Storage
) : PathItem(storage.path!!), NavigationRoot {
    init {
        require(storage.isVisible)
    }

    override val id: Long
        get() = storage.id

    override val iconRes: Int
        @DrawableRes
        get() = storage.iconRes

    override fun getTitle(context: Context): String = storage.getName(context)

    override fun getSubtitle(context: Context): String? =
        storage.linuxPath?.let { getStorageSubtitle(it, context) }

    override fun onLongClick(listener: Listener): Boolean {
        listener.launchIntent(storage.createEditIntent())
        return true
    }

    override fun getName(context: Context): String = getTitle(context)
}

private class IntentStorageItem(
    private val storage: Storage
) : NavigationItem() {
    init {
        require(storage.isVisible)
    }

    override val id: Long
        get() = storage.id

    override val iconRes: Int
        @DrawableRes
        get() = storage.iconRes

    override fun getTitle(context: Context): String = storage.getName(context)

    override fun onClick(listener: Listener) {
        listener.launchIntent(storage.createIntent()!!)
        listener.closeNavigationDrawer()
    }

    override fun onLongClick(listener: Listener): Boolean {
        listener.launchIntent(storage.createEditIntent())
        return true
    }
}

private val storageVolumeItems: List<NavigationItem>
    @Size(min = 0)
    get() =
        StorageVolumeListLiveData.value.orEmpty().filter { !it.isPrimaryCompat && it.isMounted }
            .mapNotNull { storageVolume ->
                storageVolume.pathCompat?.let { StorageVolumeItem(storageVolume, it) }
            }

private class StorageVolumeItem(
    private val storageVolume: StorageVolume,
    private val linuxPath: String
) : PathItem(Paths.get(linuxPath)), NavigationRoot {
    override val id: Long
        get() = storageVolume.hashCode().toLong()

    override val iconRes: Int
        @DrawableRes
        get() = R.drawable.ic_sd_card_white_24dp

    override fun getTitle(context: Context): String = storageVolume.getDescriptionCompat(context)

    override fun getSubtitle(context: Context): String? =
        getStorageSubtitle(linuxPath, context)

    override fun getName(context: Context): String = getTitle(context)
}

private fun getStorageSubtitle(linuxPath: String, context: Context): String? {
    var totalSpace = JavaFile.getTotalSpace(linuxPath)
    val freeSpace: Long
    when {
        totalSpace != 0L -> freeSpace = JavaFile.getFreeSpace(linuxPath)
        linuxPath == FileSystemRoot.LINUX_PATH -> {
            // Root directory may not be an actual partition on legacy Android versions (can be
            // a ramdisk instead). On modern Android the system partition will be mounted as
            // root instead so let's try with the system partition again.
            // @see https://source.android.com/devices/bootloader/system-as-root
            val systemPath = Environment.getRootDirectory().path
            totalSpace = JavaFile.getTotalSpace(systemPath)
            freeSpace = JavaFile.getFreeSpace(systemPath)
        }
        else -> freeSpace = 0
    }
    if (totalSpace == 0L) {
        return null
    }
    val freeSpaceString = freeSpace.asFileSize().formatHumanReadable(context)
    val totalSpaceString = totalSpace.asFileSize().formatHumanReadable(context)
    return context.getString(
        R.string.navigation_storage_subtitle_format, freeSpaceString, totalSpaceString
    )
}

private class AddStorageItem : NavigationItem() {
    override val id: Long = R.string.navigation_add_storage.toLong()

    @DrawableRes
    override val iconRes: Int = R.drawable.ic_add_white_24dp

    override fun getTitle(context: Context): String =
        context.getString(R.string.navigation_add_storage)

    override fun onClick(listener: Listener) {
        listener.launchIntent(AddStorageDialogActivity::class.createIntent())
    }
}

private class AddVaultItem : NavigationItem() {
    override val id: Long = R.string.navigation_add_vault.toLong()

    @DrawableRes
    override val iconRes: Int = R.drawable.ic_add_white_24dp

    override fun getTitle(context: Context): String = context.getString(R.string.navigation_add_vault)

    override fun onClick(listener: Listener) {
        listener.launchIntent(AddVaultDialogActivity::class.createIntent())
    }
}

private val standardDirectoryItems: List<NavigationItem>
    @Size(min = 0)
    get() =
        StandardDirectoriesLiveData.value.orEmpty()
            .filter { it.isEnabled }
            .map { StandardDirectoryItem(it) }

private class StandardDirectoryItem(
    private val standardDirectory: StandardDirectory
) : PathItem(Paths.get(getExternalStorageDirectory(standardDirectory.relativePath))) {
    init {
        require(standardDirectory.isEnabled)
    }

    override val id: Long
        get() = standardDirectory.id

    override val iconRes: Int
        @DrawableRes
        get() = standardDirectory.iconRes

    override fun getTitle(context: Context): String = standardDirectory.getTitle(context)

    override fun onLongClick(listener: Listener): Boolean {
        listener.launchIntent(StandardDirectoryListActivity::class.createIntent())
        return true
    }
}

val standardDirectories: List<StandardDirectory>
    get() {
        val settingsMap = Settings.STANDARD_DIRECTORY_SETTINGS.value.orEmpty().associateBy { it.id }
        return defaultStandardDirectories.map {
            val settings = settingsMap[it.key]
            if (settings != null) it.withSettings(settings) else it
        }
    }

private const val relativePathSeparator = ":"

private val defaultStandardDirectories: List<StandardDirectory>
    // HACK: Show QQ, TIM and WeChat standard directories based on whether the directory exists.
    get() =
        DEFAULT_STANDARD_DIRECTORIES.mapNotNull {
            when (it.iconRes) {
                R.drawable.ic_qq_white_24dp, R.drawable.ic_tim_white_24dp,
                R.drawable.ic_wechat_white_24dp -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Direct access to Android/data is blocked since Android 11.
                        null
                    } else {
                        for (relativePath in it.relativePath.split(relativePathSeparator)) {
                            val path = getExternalStorageDirectory(relativePath)
                            if (JavaFile.isDirectory(path)) {
                                return@mapNotNull it.copy(relativePath = relativePath)
                            }
                        }
                        null
                    }
                }
                else -> it
            }
        }

// @see android.os.Environment#STANDARD_DIRECTORIES
private val DEFAULT_STANDARD_DIRECTORIES = listOf(
    StandardDirectory(
        R.drawable.ic_alarm_white_24dp, R.string.navigation_standard_directory_alarms,
        Environment.DIRECTORY_ALARMS, false
    ),
    StandardDirectory(
        R.drawable.ic_camera_white_24dp, R.string.navigation_standard_directory_dcim,
        Environment.DIRECTORY_DCIM, true
    ),
    StandardDirectory(
        R.drawable.ic_document_white_24dp, R.string.navigation_standard_directory_documents,
        Environment.DIRECTORY_DOCUMENTS, false),
    StandardDirectory(
        R.drawable.ic_download_white_24dp, R.string.navigation_standard_directory_downloads,
        Environment.DIRECTORY_DOWNLOADS, true
    ),
    StandardDirectory(
        R.drawable.ic_video_white_24dp, R.string.navigation_standard_directory_movies,
        Environment.DIRECTORY_MOVIES, true
    ),
    StandardDirectory(
        R.drawable.ic_audio_white_24dp, R.string.navigation_standard_directory_music,
        Environment.DIRECTORY_MUSIC, true
    ),
    StandardDirectory(
        R.drawable.ic_notification_white_24dp,
        R.string.navigation_standard_directory_notifications, Environment.DIRECTORY_NOTIFICATIONS,
        false
    ),
    StandardDirectory(
        R.drawable.ic_image_white_24dp, R.string.navigation_standard_directory_pictures,
        Environment.DIRECTORY_PICTURES, true
    ),
    StandardDirectory(
        R.drawable.ic_podcast_white_24dp, R.string.navigation_standard_directory_podcasts,
        Environment.DIRECTORY_PODCASTS, false
    ),
    StandardDirectory(
        R.drawable.ic_ringtone_white_24dp, R.string.navigation_standard_directory_ringtones,
        Environment.DIRECTORY_RINGTONES, false
    ),
    StandardDirectory(
        R.drawable.ic_qq_white_24dp, R.string.navigation_standard_directory_qq,
        listOf("Android/data/com.tencent.mobileqq/Tencent/QQfile_recv", "Tencent/QQfile_recv")
            .joinToString(relativePathSeparator), true
    ),
    StandardDirectory(
        R.drawable.ic_tim_white_24dp, R.string.navigation_standard_directory_tim,
        listOf("Android/data/com.tencent.tim/Tencent/TIMfile_recv", "Tencent/TIMfile_recv")
            .joinToString(relativePathSeparator), true
    ),
    StandardDirectory(
        R.drawable.ic_wechat_white_24dp, R.string.navigation_standard_directory_wechat,
        listOf("Android/data/com.tencent.mm/MicroMsg/Download", "Tencent/MicroMsg/Download")
            .joinToString(relativePathSeparator), true
    )
)

internal fun getExternalStorageDirectory(relativePath: String): String =
    @Suppress("DEPRECATION")
    Environment.getExternalStoragePublicDirectory(relativePath).path

private val screenshotsDirectoryPath: String by lazy {
    Paths.get(
        getExternalStorageDirectory(Environment.DIRECTORY_PICTURES),
        EnvironmentCompat2.DIRECTORY_SCREENSHOTS
    ).toString()
}

private fun isScreenshotsBookmark(bookmarkDirectory: BookmarkDirectory): Boolean =
    bookmarkDirectory.path.toLegacyPathOrNull()?.toString() == screenshotsDirectoryPath

private val bookmarkDirectoryItems: List<NavigationItem>
    @Size(min = 0)
    get() = Settings.BOOKMARK_DIRECTORIES.value.orEmpty()
        .filterNot { isScreenshotsBookmark(it) }
        .mapNotNull { BookmarkDirectoryItem.createOrNull(it) }

private val recycleBinItem: NavigationItem?
    get() = if (Settings.RECYCLE_BIN.valueCompat) RecycleBinItem() else null

private class RecycleBinItem : PathItem(RecycleBinManager.recycleBinRootPath) {
    override val id: Long = R.string.navigation_recycle_bin.toLong()

    @DrawableRes
    override val iconRes: Int = R.drawable.ic_delete_control_normal_24dp

    override fun onClick(listener: Listener) {
        runCatching { RecycleBinManager.ensureRecycleBinExists(path) }
            .onFailure {
                com.wisso.wizefiles.util.AppLog.e("Error", "Failed to create recycle bin", it)
            }
        super.onClick(listener)
    }

    override fun getTitle(context: Context): String = context.getString(R.string.navigation_recycle_bin)
}

private class BookmarkDirectoryItem private constructor(
    private val bookmarkDirectory: BookmarkDirectory,
    path: Path
) : PathItem(path) {
    companion object {
        fun createOrNull(bookmarkDirectory: BookmarkDirectory): BookmarkDirectoryItem? {
            val path = bookmarkDirectory.path.toLegacyPathOrNull() ?: return null
            return BookmarkDirectoryItem(bookmarkDirectory, path)
        }
    }

    // We cannot simply use super.getId() because different bookmark directories may have
    // the same path.
    override val id: Long
        get() = bookmarkDirectory.id

    @DrawableRes
    override val iconRes: Int = R.drawable.ic_directory_white_24dp

    override fun getTitle(context: Context): String = bookmarkDirectory.name

    override fun onLongClick(listener: Listener): Boolean {
        listener.launchIntent(
            EditBookmarkDirectoryDialogActivity::class.createIntent()
                .putArgs(EditBookmarkDirectoryDialogFragment.Args(bookmarkDirectory))
        )
        return true
    }
}

private val transferCenterMenuItem: NavigationItem = ActionMenuItem(
    R.drawable.ic_transfer_center_control_normal_24dp,
    R.string.transfer_center_title,
    NavigationAction.TRANSFER_CENTER
)

private val toolMenuItems: List<NavigationItem>
    @Size(5)
    get() = listOf(
        ActionMenuItem(
            R.drawable.ic_storage_treemap_24dp,
            R.string.file_list_action_storage_cleaner,
            NavigationAction.STORAGE_CLEANER
        ),
        ActionMenuItem(
            R.drawable.ic_sync_backup_control_normal_24dp,
            R.string.sync_profiles_title,
            NavigationAction.SYNC_BACKUP
        ),
        ActionMenuItem(
            R.drawable.ic_share_control_normal_24dp,
            R.string.local_share_title,
            NavigationAction.LOCAL_SHARING
        ),
        ActionMenuItem(
            R.drawable.ic_nearby_transfer_control_normal_24dp,
            R.string.nearby_transfer_title,
            NavigationAction.NEARBY_TRANSFER
        ),
        IntentMenuItem(
            R.drawable.ic_bs_app_indicator_24dp, R.string.navigation_app_manager,
            AppManagerActivity::class.createIntent()
        )
    )

private val settingsMenuItems: List<NavigationItem>
    @Size(2)
    get() = listOf(
        IntentMenuItem(
            R.drawable.ic_settings_white_24dp, R.string.navigation_settings,
            SettingsActivity::class.createIntent()
        ),
        IntentMenuItem(
            R.drawable.ic_about_white_24dp, R.string.navigation_about,
            AboutActivity::class.createIntent()
        )
    )

private abstract class MenuItem(
    @DrawableRes override val iconRes: Int,
    @StringRes val titleRes: Int
) : NavigationItem() {
    override fun getTitle(context: Context): String = context.getString(titleRes)
}

private class ActionMenuItem(
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
    private val action: NavigationAction
) : MenuItem(iconRes, titleRes) {
    override val id: Long = action.stableId

    override fun onClick(listener: Listener) {
        listener.launchNavigationAction(action)
        listener.closeNavigationDrawer()
    }
}

private class IntentMenuItem(
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
    private val intent: Intent
) : MenuItem(iconRes, titleRes) {
    override val id: Long
        get() = intent.component.hashCode().toLong()

    override fun onClick(listener: Listener) {
        listener.launchIntent(intent)
        listener.closeNavigationDrawer()
    }
}