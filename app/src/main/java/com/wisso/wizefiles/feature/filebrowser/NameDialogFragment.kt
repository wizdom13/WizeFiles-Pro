package com.wisso.wizefiles.feature.filebrowser

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.DialogNameBinding
import com.wisso.wizefiles.databinding.IncludeNameDialogNameBinding
import com.wisso.wizefiles.util.hideTextInputLayoutErrorOnTextChange
import com.wisso.wizefiles.util.layoutInflater
import com.wisso.wizefiles.util.setOnEditorConfirmActionListener
import com.wisso.wizefiles.util.showImeWithResize

abstract class NameDialogFragment : AppCompatDialogFragment() {
    private lateinit var _binding: Binding
    protected open val binding: Binding
        get() = _binding

    protected open val listener: Listener
        get() = requireParentFragment() as Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(titleRes)
            .apply {
                _binding = onInflateBinding(context.layoutInflater)
                if (savedInstanceState == null) {
                    val initialName = initialName
                    if (initialName != null) {
                        binding.nameEdit.setText(initialName)
                        binding.nameEdit.setSelection(0, initialName.length)
                    }
                }
                binding.clearNameErrorOnTextChange()
                binding.nameEdit.setOnEditorConfirmActionListener { onOk() }
                setView(binding.root)
            }
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .apply {
                window!!.showImeWithResize()
                // Override the listener here so that we have control over when to close the dialog.
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { onOk() }
                }
            }

    @get:StringRes
    protected abstract val titleRes: Int

    protected open val initialName: String? = null

    protected open fun onInflateBinding(inflater: LayoutInflater): Binding =
        Binding.inflate(inflater)

    protected fun inflateNameBinding(
        inflater: LayoutInflater,
        @LayoutRes layoutRes: Int
    ): Binding = Binding.inflate(inflater, layoutRes)

    private fun onOk() {
        val name = name
        if (!isNameValid(name)) {
            return
        }
        onOk(name)
        dismiss()
    }

    protected open val name: String
        get() = binding.nameEdit.text.toString().trim()

    protected open fun isNameValid(name: String): Boolean {
        if (name == initialName) {
            dismiss()
            return false
        }
        return true
    }

    protected abstract fun onOk(name: String)

    protected open class Binding protected constructor(
        val root: View,
        private val nameLayout: TextInputLayout?,
        val nameEdit: EditText
    ) {
        fun clearNameErrorOnTextChange() {
            val nameLayout = nameLayout
            if (nameLayout != null) {
                nameEdit.hideTextInputLayoutErrorOnTextChange(nameLayout)
            } else {
                nameEdit.doAfterTextChanged { nameEdit.error = null }
            }
        }

        fun showNameError(error: CharSequence) {
            val nameLayout = nameLayout
            if (nameLayout != null) {
                nameLayout.error = error
            } else {
                nameEdit.error = error
            }
        }

        companion object {
            fun inflate(inflater: LayoutInflater): Binding {
                val binding = DialogNameBinding.inflate(inflater)
                val bindingRoot = binding.root
                val nameBinding = IncludeNameDialogNameBinding.bind(bindingRoot)
                return Binding(bindingRoot, nameBinding.nameLayout, nameBinding.nameEdit)
            }

            fun inflate(inflater: LayoutInflater, @LayoutRes layoutRes: Int): Binding {
                val bindingRoot = inflater.inflate(layoutRes, null, false)
                val nameEdit = bindingRoot.findViewById<EditText>(R.id.nameEdit)
                return Binding(bindingRoot, null, nameEdit)
            }
        }
    }

    interface Listener
}
