// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.viewer.text

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import java.nio.file.Path
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.FragmentTextEditorBinding
import com.wisso.wizefiles.util.DataState
import com.wisso.wizefiles.util.showOptionalIcons
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.addOnBackPressedCallback
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.extraPath
import com.wisso.wizefiles.util.fadeInUnsafe
import com.wisso.wizefiles.util.fadeOutUnsafe
import com.wisso.wizefiles.util.showToast
import com.wisso.wizefiles.util.viewModels
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import java.nio.charset.Charset
import com.wisso.wizefiles.feature.filebrowser.RequestAllFilesAccessContract

class TextEditorFragment : Fragment(), ConfirmReloadDialogFragment.Listener,
    ConfirmCloseDialogFragment.Listener {
    private val args by args<Args>()
    private lateinit var argsFile: Path

    private lateinit var binding: FragmentTextEditorBinding

    private lateinit var menuBinding: MenuBinding

    private val viewModel by viewModels { { TextEditorViewModel(argsFile) } }

    private lateinit var onBackPressedCallback: OnBackPressedCallback

    private var isSettingText = false
    private var pendingAccessSave: String? = null
    private val requestAllFilesAccess = registerForActivityResult(RequestAllFilesAccessContract()) { granted ->
        val pendingText = pendingAccessSave
        pendingAccessSave = null
        if (granted && pendingText != null) {
            viewModel.writeFile(argsFile, pendingText, requireContext())
        } else if (!granted) {
            showToast(R.string.text_editor_save_failure)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                ConfirmCloseDialogFragment.show(this@TextEditorFragment)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        FragmentTextEditorBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val argsFile = args.intent.extraPath?.toLegacyPathOrNull()
        if (argsFile == null) {
            showToast(R.string.text_editor_load_failure)
            finish()
            return
        }
        this.argsFile = argsFile

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        addOnBackPressedCallback(onBackPressedCallback)
        (activity as MenuHost).addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: android.view.MenuInflater) {
                menuBinding = MenuBinding.inflate(menu, menuInflater)
                menu.showOptionalIcons()
            }

            override fun onPrepareMenu(menu: Menu) {
                updateSaveMenuItem()
                updateEncodingMenuItems()
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean =
                when (item.itemId) {
                    R.id.action_save -> {
                        save()
                        true
                    }
                    R.id.action_reload -> {
                        onReload()
                        true
                    }
                    Menu.FIRST -> {
                        viewModel.encoding.value = Charset.forName(item.titleCondensed!!.toString())
                        true
                    }
                    else -> false
                }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isTextChanged.collect {
                        onBackPressedCallback.isEnabled = viewModel.isTextChanged.value
                    }
                }
                launch { viewModel.encoding.collect { onEncodingChanged(it) } }
                launch { viewModel.textState.collect { onTextStateChanged(it) } }
                launch { viewModel.isTextChanged.collect { onIsTextChangedChanged(it) } }
                launch { viewModel.canSave.collect { updateSaveMenuItem() } }
                launch {
                    viewModel.writeFileResults.collect { result ->
                        when (result) {
                            TextEditorWriteResult.Saved ->
                                showToast(R.string.text_editor_save_success)
                            TextEditorWriteResult.AccessRequired -> {
                                pendingAccessSave = binding.textEdit.text.toString()
                                requestAllFilesAccess.launch(Unit)
                            }
                            TextEditorWriteResult.Failed ->
                                showToast(R.string.text_editor_save_failure)
                        }
                    }
                }
            }
        }

        // Manually save and restore state in view model to avoid TransactionTooLargeException.
        binding.textEdit.isSaveEnabled = false
        val textEditSavedState = viewModel.removeEditTextSavedState()
        if (textEditSavedState != null) {
            binding.textEdit.onRestoreInstanceState(textEditSavedState)
        }
        binding.textEdit.doAfterTextChanged {
            if (isSettingText) {
                return@doAfterTextChanged
            }
            // Might happen if the animation is running and user is quick enough.
            if (viewModel.textState.value !is DataState.Success) {
                return@doAfterTextChanged
            }
            viewModel.onTextChanged(it?.toString().orEmpty())
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        viewModel.setEditTextSavedState(binding.textEdit.onSaveInstanceState())
    }

    fun onSupportNavigateUp(): Boolean {
        if (onBackPressedCallback.isEnabled) {
            onBackPressedCallback.handleOnBackPressed()
            return true
        }
        return false
    }

    override fun finish() {
        requireActivity().finish()
    }

    private fun onEncodingChanged(encoding: Charset) {
        updateEncodingMenuItems()
    }

    private fun updateEncodingMenuItems() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        val charsetName = viewModel.encoding.value.name()
        val charsetItem = menuBinding.encodingSubMenu.children
            .find { it.titleCondensed == charsetName }!!
        charsetItem.isChecked = true
    }

    private fun onTextStateChanged(state: DataState<String>) {
        updateTitle()
        when (state) {
            is DataState.Loading -> {
                binding.progress.fadeInUnsafe()
                binding.errorText.fadeOutUnsafe()
                binding.textEdit.fadeOutUnsafe()
            }
            is DataState.Success -> {
                binding.progress.fadeOutUnsafe()
                binding.errorText.fadeOutUnsafe()
                binding.textEdit.fadeInUnsafe()
                if (!viewModel.isTextChanged.value) {
                    setText(state.data)
                }
            }
            is DataState.Error -> {
                com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", state.throwable)
                binding.progress.fadeOutUnsafe()
                binding.errorText.fadeInUnsafe()
                binding.errorText.setText(R.string.text_editor_load_failure)
                binding.textEdit.fadeOutUnsafe()
            }
        }
    }

    private fun setText(text: String?) {
        isSettingText = true
        binding.textEdit.setText(text)
        isSettingText = false
        viewModel.onTextLoaded(text.orEmpty())
    }

    private fun onIsTextChangedChanged(changed: Boolean) {
        updateTitle()
        updateSaveMenuItem()
    }

    private fun updateTitle() {
        val fileName = viewModel.file.value.fileName.toString()
        val changed = viewModel.isTextChanged.value
        requireActivity().title = getString(
            if (changed) {
                R.string.text_editor_title_changed_format
            } else {
                R.string.text_editor_title_format
            }, fileName
        )
    }

    private fun onReload() {
        if (viewModel.isTextChanged.value) {
            ConfirmReloadDialogFragment.show(this)
        } else {
            reload()
        }
    }

    override fun reload() {
        viewModel.discardTextChanges()
        viewModel.reload()
    }

    private fun save() {
        val text = binding.textEdit.text.toString()
        viewModel.writeFile(argsFile, text, requireContext())
    }

    private fun updateSaveMenuItem() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        menuBinding.saveItem.isEnabled = viewModel.canSave.value
    }

    @Parcelize
    class Args(val intent: Intent) : ParcelableArgs

    private class MenuBinding private constructor(
        val menu: Menu,
        val saveItem: MenuItem,
        val encodingSubMenu: SubMenu
    ) {
        companion object {
            fun inflate(menu: Menu, inflater: MenuInflater): MenuBinding {
                inflater.inflate(R.menu.menu_text_editor, menu)
                val encodingSubMenu = menu.findItem(R.id.action_encoding).subMenu!!
                for ((charsetName, charset) in Charset.availableCharsets()) {
                    // HACK: Use titleCondensed to store charset name.
                    encodingSubMenu.add(Menu.NONE, Menu.FIRST, Menu.NONE, charset.displayName())
                        .setIcon(R.drawable.ic_text_control_normal_24dp)
                        .titleCondensed = charsetName
                }
                encodingSubMenu.setGroupCheckable(Menu.NONE, true, true)
                encodingSubMenu.showOptionalIcons()
                return MenuBinding(menu, menu.findItem(R.id.action_save), encodingSubMenu)
            }
        }
    }
}
