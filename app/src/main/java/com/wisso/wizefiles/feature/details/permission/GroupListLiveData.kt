// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.details.permission

import com.wisso.wizefiles.provider.os.syscall.Syscall.endgrent
import com.wisso.wizefiles.provider.os.syscall.Syscall.getgrent
import com.wisso.wizefiles.provider.os.syscall.Syscall.setgrent

class GroupListLiveData : PrincipalListLiveData() {
    override val androidPrincipals: MutableList<PrincipalItem>
        @Throws(Exception::class)
        get() {
            val groups = mutableListOf<PrincipalItem>()
            setgrent()
            try {
                while (true) {
                    val structGroup = getgrent() ?: break
                    val group = PrincipalItem(
                        structGroup.gr_gid, structGroup.gr_name?.toString(), emptyList(),
                        emptyList()
                    )
                    groups.add(group)
                }
            } finally {
                endgrent()
            }
            return groups
        }

    /*
     * @see https://android.googlesource.com/platform/bionic/+/android10-release/libc/bionic/grp_grd.cpp
     *      print_app_name_from_gid()
     */
    override fun getAppPrincipalName(uid: Int): String {
        val userId = uid / AID_USER_OFFSET
        val appId = uid % AID_USER_OFFSET
        return when {
            appId > AID_ISOLATED_START -> "u${userId}_i${appId - AID_ISOLATED_START}"
            userId == 0 && appId in AID_SHARED_GID_START..AID_SHARED_GID_END ->
                "all_a${appId - AID_SHARED_GID_START}"
            appId in AID_CACHE_GID_START..AID_CACHE_GID_END ->
                "u${userId}_a${appId - AID_CACHE_GID_START}_cache"
            else -> "u${userId}_a${appId - AID_APP_START}"
        }
    }
}
