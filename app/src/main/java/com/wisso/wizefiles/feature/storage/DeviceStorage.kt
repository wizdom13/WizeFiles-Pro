package com.wisso.wizefiles.storage

import android.content.Context
import android.content.Intent
import android.os.storage.StorageVolume
import androidx.annotation.DrawableRes
import java.nio.file.Path
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.android.compat.getDescriptionCompat
import com.wisso.wizefiles.core.android.compat.isPrimaryCompat
import com.wisso.wizefiles.core.android.compat.pathCompat
import com.wisso.wizefiles.provider.os.LinuxFileSystemProvider
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.valueCompat

sealed class DeviceStorage : Storage() {
    override val description: String
        get() = linuxPath

    override val path: Path
        get() = LinuxFileSystemProvider.fileSystem.getPath(linuxPath)

    abstract override val linuxPath: String

    override fun createEditIntent(): Intent =
        EditDeviceStorageDialogActivity::class.createIntent()
            .putArgs(EditDeviceStorageDialogFragment.Args(this))

    fun copy_(
        customName: String? = this.customName,
        isVisible: Boolean = this.isVisible
    ): DeviceStorage =
        when (this) {
            is FileSystemRoot -> copy(customName, isVisible)
            is PrimaryStorageVolume -> copy(customName, isVisible)
        }
}

@Parcelize
data class FileSystemRoot(
    override val customName: String?,
    override val isVisible: Boolean
) : DeviceStorage() {
    override val id: Long
        get() = "FileSystemRoot".hashCode().toLong()

    override val iconRes: Int
        @DrawableRes
        get() = R.drawable.ic_device_white_24dp

    override fun getDefaultName(context: Context): String =
        context.getString(R.string.storage_file_system_root_title)

    override val linuxPath: String
        get() = LINUX_PATH

    companion object {
        const val LINUX_PATH = "/"
    }
}

@Parcelize
data class PrimaryStorageVolume(
    override val customName: String?,
    override val isVisible: Boolean
) : DeviceStorage() {
    override val id: Long
        get() = "PrimaryStorageVolume".hashCode().toLong()

    override val iconRes: Int
        @DrawableRes
        get() = R.drawable.ic_sd_card_white_24dp

    override fun getDefaultName(context: Context): String =
        storageVolume.getDescriptionCompat(context)

    override val linuxPath: String
        get() = storageVolume.pathCompat ?: FileSystemRoot.LINUX_PATH

    private val storageVolume: StorageVolume
        get() = StorageVolumeListLiveData.valueCompat.find { it.isPrimaryCompat }!!
}
