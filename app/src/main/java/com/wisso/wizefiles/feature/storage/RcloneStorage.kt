package com.wisso.wizefiles.storage

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import com.wisso.wizefiles.R
import com.wisso.wizefiles.provider.rclone.createRcloneRootPath
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.putArgs
import java.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlin.random.Random

@Parcelize
data class RcloneStorage(
    override val id: Long,
    override val customName: String?,
    val remoteName: String,
    val providerType: String,
    val rootPath: String
) : Storage() {
    constructor(
        id: Long?,
        customName: String?,
        remoteName: String,
        providerType: String,
        rootPath: String
    ) : this(id ?: Random.nextLong(), customName, remoteName, providerType, rootPath)

    override val iconRes: Int
        @DrawableRes
        get() = rcloneProviderIconRes(providerType)

    override fun getDefaultName(context: Context): String =
        remoteName

    override val description: String
        get() = providerType

    override val path: Path
        get() = createRcloneRootPath(remoteName).resolve(rootPath.trimStart('/'))

    override fun createEditIntent(): Intent =
        EditRcloneStorageActivity::class.createIntent().putArgs(
            EditRcloneStorageFragment.Args(this)
        )
}

@DrawableRes
internal fun rcloneProviderIconRes(providerType: String): Int =
    when (providerType.trim().lowercase()) {
        "drive" -> R.drawable.ic_provider_google_drive_white_24dp
        "onedrive" -> R.drawable.ic_provider_onedrive_white_24dp
        "dropbox" -> R.drawable.ic_provider_dropbox_white_24dp
        "box" -> R.drawable.ic_provider_box_white_24dp
        "pcloud" -> R.drawable.ic_provider_pcloud_white_24dp
        "mega" -> R.drawable.ic_provider_mega_white_24dp
        "webdav" -> R.drawable.ic_provider_webdav_white_24dp
        "s3" -> R.drawable.ic_provider_s3_white_24dp
        else -> R.drawable.ic_cloud_white_24dp
    }
