package com.wisso.wizefiles.navigation

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import java.nio.file.Path
import com.wisso.wizefiles.core.android.compat.getDrawableCompat

enum class NavigationAction(val stableId: Long) {
    STORAGE_CLEANER(-1_001L),
    TRANSFER_CENTER(-1_002L),
    SYNC_BACKUP(-1_003L),
    LOCAL_SHARING(-1_004L),
    NEARBY_TRANSFER(-1_005L)
}

abstract class NavigationItem {
    abstract val id: Long

    fun getIcon(context: Context): Drawable = context.getDrawableCompat(iconRes!!)

    @get:DrawableRes
    protected abstract val iconRes: Int?

    abstract fun getTitle(context: Context): String

    open fun getSubtitle(context: Context): String? = null

    open fun isChecked(listener: Listener): Boolean = false

    abstract fun onClick(listener: Listener)

    open fun onLongClick(listener: Listener): Boolean = false

    interface Listener {
        val currentPath: Path
        fun navigateTo(path: Path)
        fun navigateToRoot(path: Path)
        fun launchIntent(intent: Intent)
        fun launchNavigationAction(action: NavigationAction)
        fun closeNavigationDrawer()
    }
}
