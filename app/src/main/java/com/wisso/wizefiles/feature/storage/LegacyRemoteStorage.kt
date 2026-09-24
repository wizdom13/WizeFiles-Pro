package com.wisso.wizefiles.storage

import android.content.Context
import android.content.Intent
import java.nio.file.Path
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * Compatibility-only storage used when decoding removed remote provider entries from old settings.
 */
@Parcelize
class LegacyRemoteStorage(
    override val id: Long,
    override val customName: String?
) : Storage() {
    override fun getDefaultName(context: Context): String = ""

    override val description: String
        get() = ""

    @IgnoredOnParcel
    override val path: Path? = null

    override fun createEditIntent(): Intent = Intent()

    override val isVisible: Boolean
        get() = false
}
