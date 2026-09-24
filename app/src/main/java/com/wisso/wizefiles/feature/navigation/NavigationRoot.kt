package com.wisso.wizefiles.navigation

import android.content.Context
import androidx.annotation.DrawableRes
import java.nio.file.Path

interface NavigationRoot {
    val path: Path

    @get:DrawableRes
    val iconRes: Int

    fun getName(context: Context): String
}
