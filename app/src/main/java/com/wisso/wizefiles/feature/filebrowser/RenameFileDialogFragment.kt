package com.wisso.wizefiles.feature.filebrowser

import android.app.Dialog
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.show

class RenameFileDialogFragment : FileNameDialogFragment() {
    private val args by args<Args>()

    override val listener: Listener
        get() = super.listener as Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)

        if (savedInstanceState == null) {
            binding.nameEdit.setSelection(0, args.file.baseName.length)
        }
        return dialog
    }

    @StringRes
    override val titleRes: Int = R.string.rename

    override val initialName: String?
        get() = args.file.name

    override fun onOk(name: String) {
        listener.renameFile(args.file, name)
    }

    companion object {
        fun show(file: FileItem, fragment: Fragment) {
            RenameFileDialogFragment().putArgs(Args(file)).show(fragment)
        }
    }

    @Parcelize
    class Args(val file: FileItem) : ParcelableArgs

    interface Listener : FileNameDialogFragment.Listener {
        fun renameFile(file: FileItem, newName: String)
    }
}
