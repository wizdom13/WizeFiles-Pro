package com.wisso.wizefiles.feature.details.permission

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import java.nio.file.Path
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.feature.filejobs.FileOperationService
import com.wisso.wizefiles.provider.common.PosixUser
import com.wisso.wizefiles.provider.common.toByteString
import com.wisso.wizefiles.util.SelectionLiveData
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.show
import com.wisso.wizefiles.util.viewModels

class SetOwnerDialogFragment : SetPrincipalDialogFragment() {
    override val viewModel: SetPrincipalViewModel by viewModels { { SetOwnerViewModel() } }

    @StringRes
    override val titleRes: Int = R.string.file_properties_permission_set_owner_title

    override fun createAdapter(selectionLiveData: SelectionLiveData<Int>): PrincipalListAdapter =
        UserListAdapter(selectionLiveData)

    override fun setPrincipal(path: Path, principal: PrincipalItem, recursive: Boolean) {
        val owner = PosixUser(principal.id, principal.name?.toByteString())
        FileOperationService.setOwner(path, owner, recursive, requireContext())
    }

    companion object {
        fun show(file: FileItem, isDirectory: Boolean, ownerId: Int, fragment: Fragment) {
            SetOwnerDialogFragment().putArgs(Args(file, isDirectory, ownerId)).show(fragment)
        }
    }
}
