package com.wisso.wizefiles.feature.filebrowser

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.wisso.wizefiles.R
import com.wisso.wizefiles.util.show

class CreateFileDialogFragment : FileNameDialogFragment() {
    override val listener: Listener
        get() = super.listener as Listener

    @StringRes
    override val titleRes: Int = R.string.file_list_action_create_file

    override val useCreateEntryDialogStyle: Boolean = true

    override fun onOk(name: String) {
        listener.createFile(name)
    }

    companion object {
        fun show(fragment: Fragment) {
            CreateFileDialogFragment().show(fragment)
        }
    }

    interface Listener : FileNameDialogFragment.Listener {
        fun createFile(name: String)
    }
}
