package com.wisso.wizefiles.storage

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import java.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import com.wisso.wizefiles.R
import com.wisso.wizefiles.security.SecretStringParceler
import com.wisso.wizefiles.provider.smb.client.Authority
import com.wisso.wizefiles.provider.smb.createSmbRootPath
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.putArgs
import kotlin.random.Random

@Parcelize
class SmbServer(
    override val id: Long,
    override val customName: String?,
    val authority: Authority,
    val password: @WriteWith<SecretStringParceler> String,
    val relativePath: String
) : Storage() {
    constructor(
        id: Long?,
        customName: String?,
        authority: Authority,
        password: String,
        relativePath: String
    ) : this(id ?: Random.nextLong(), customName, authority, password, relativePath)

    override val iconRes: Int
        @DrawableRes
        get() = R.drawable.ic_computer_white_24dp

    override fun getDefaultName(context: Context): String =
        if (relativePath.isNotEmpty()) "$authority/$relativePath" else authority.toString()

    override val description: String
        get() = authority.toString()

    override val path: Path
        get() = authority.createSmbRootPath().resolve(relativePath)

    override fun createEditIntent(): Intent =
        EditSmbServerActivity::class.createIntent().putArgs(EditSmbServerFragment.Args(this))
}
