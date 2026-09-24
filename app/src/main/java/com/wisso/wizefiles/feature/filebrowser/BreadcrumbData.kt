package com.wisso.wizefiles.feature.filebrowser

import android.content.Context
import java.nio.file.Path

data class BreadcrumbData(
    val paths: List<Path>,
    val nameProducers: List<(Context) -> String>,
    val iconResIds: List<Int?>,
    val selectedIndex: Int
)
