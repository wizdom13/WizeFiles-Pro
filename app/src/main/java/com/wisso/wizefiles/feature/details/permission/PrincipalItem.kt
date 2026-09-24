package com.wisso.wizefiles.feature.details.permission

import android.content.pm.ApplicationInfo

class PrincipalItem(
    val id: Int,
    val name: String?,
    val applicationInfos: List<ApplicationInfo>,
    val applicationLabels: List<String>
)
