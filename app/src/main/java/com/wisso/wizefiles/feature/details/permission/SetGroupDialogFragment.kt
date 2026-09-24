// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.details.permission

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import java.nio.file.Path
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.feature.filejobs.FileOperationService
import com.wisso.wizefiles.provider.common.PosixGroup
import com.wisso.wizefiles.provider.common.toByteString
import com.wisso.wizefiles.util.SelectionLiveData
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.show
import com.wisso.wizefiles.util.viewModels

class SetGroupDialogFragment : SetPrincipalDialogFragment() {
    override val viewModel: SetPrincipalViewModel by viewModels { { SetGroupViewModel() } }

    @StringRes
    override val titleRes: Int = R.string.file_properties_permission_set_group_title

    override fun createAdapter(selectionLiveData: SelectionLiveData<Int>): PrincipalListAdapter =
        GroupListAdapter(selectionLiveData)

    override fun setPrincipal(path: Path, principal: PrincipalItem, recursive: Boolean) {
        val group = PosixGroup(principal.id, principal.name?.toByteString())
        FileOperationService.setGroup(path, group, recursive, requireContext())
    }

    companion object {
        fun show(file: FileItem, isDirectory: Boolean, groupId: Int, fragment: Fragment) {
            SetGroupDialogFragment().putArgs(Args(file, isDirectory, groupId)).show(fragment)
        }
    }
}
