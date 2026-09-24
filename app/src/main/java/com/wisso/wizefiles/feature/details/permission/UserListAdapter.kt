package com.wisso.wizefiles.feature.details.permission

import androidx.annotation.DrawableRes
import com.wisso.wizefiles.R
import com.wisso.wizefiles.util.SelectionLiveData

class UserListAdapter(
    selectionLiveData: SelectionLiveData<Int>
) : PrincipalListAdapter(selectionLiveData) {
    @DrawableRes
    override val principalIconRes: Int = R.drawable.ic_person_control_normal_24dp
}
