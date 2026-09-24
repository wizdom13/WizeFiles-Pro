// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.os.Parcel
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.android.compat.requireViewByIdCompat
import com.wisso.wizefiles.databinding.DialogFileJobErrorBinding
import com.wisso.wizefiles.provider.common.PosixFileStore
import com.wisso.wizefiles.util.ActionState
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.ParcelableParceler
import com.wisso.wizefiles.util.ParcelableState
import com.wisso.wizefiles.util.RemoteCallback
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.finish
import com.wisso.wizefiles.util.getArgs
import com.wisso.wizefiles.util.getState
import com.wisso.wizefiles.util.isReady
import com.wisso.wizefiles.util.isRunning
import com.wisso.wizefiles.util.layoutInflater
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.putState
import com.wisso.wizefiles.util.readParcelable
import com.wisso.wizefiles.util.showToast
import com.wisso.wizefiles.util.viewModels

class FileOperationErrorDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private val viewModel by viewModels { { FileOperationErrorViewModel() } }

    private lateinit var binding: DialogFileJobErrorBinding

    private var isListenerNotified = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (args.readOnlyFileStore != null) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch { viewModel.remountState.collect { onRemountStateChanged(it) } }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putState(State(binding.allCheck.isChecked))
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(args.title)
            .setMessage(args.message)
            .apply {
                binding = DialogFileJobErrorBinding.inflate(context.layoutInflater)
                val hasReadOnlyFileStore = args.readOnlyFileStore != null
                binding.remountButton.isVisible = hasReadOnlyFileStore
                if (hasReadOnlyFileStore) {
                    updateRemountButton()
                    binding.remountButton.setOnClickListener { remount() }
                }
                binding.allSpace.isVisible = !hasReadOnlyFileStore && args.showAll
                binding.allCheck.isVisible = args.showAll
                if (savedInstanceState != null) {
                    binding.allCheck.isChecked = savedInstanceState.getState<State>().isAllChecked
                }

            }
            .setPositiveButton(args.positiveButtonText, ::onDialogButtonClick)
            .setNegativeButton(args.negativeButtonText, ::onDialogButtonClick)
            .setNeutralButton(args.neutralButtonText, ::onDialogButtonClick)
            .create()
            .apply { setCanceledOnTouchOutside(false) }

    private fun remount() {
        if (!viewModel.remountState.value.isReady || !args.readOnlyFileStore!!.isReadOnly) {
            return
        }
        viewModel.remount(args.readOnlyFileStore!!)
    }

    private fun onRemountStateChanged(state: ActionState<PosixFileStore, Unit>) {
        when (state) {
            is ActionState.Ready, is ActionState.Running -> updateRemountButton()
            is ActionState.Success -> viewModel.finishRemounting()
            is ActionState.Error -> {
                val throwable = state.throwable
                com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", throwable)
                showToast(throwable.toString())
                viewModel.finishRemounting()
            }
        }
    }

    private fun updateRemountButton() {
        val textRes = when {
            viewModel.remountState.value.isRunning -> R.string.file_job_remount_loading_format
            args.readOnlyFileStore!!.isReadOnly -> R.string.file_job_remount_format
            else -> R.string.file_job_remount_success_format
        }
        binding.remountButton.text = getString(textRes, args.readOnlyFileStore!!.name())
    }

    private fun onDialogButtonClick(dialog: DialogInterface, which: Int) {
        val action = when (which) {
            DialogInterface.BUTTON_POSITIVE -> FileOperationErrorAction.POSITIVE
            DialogInterface.BUTTON_NEGATIVE -> FileOperationErrorAction.NEGATIVE
            DialogInterface.BUTTON_NEUTRAL -> FileOperationErrorAction.NEUTRAL
            else -> throw AssertionError(which)
        }
        notifyListenerOnce(action, args.showAll && binding.allCheck.isChecked)
        finish()
    }

    override fun onStart() {
        super.onStart()

        if (binding.root.parent == null) {
            val dialog = requireDialog() as AlertDialog
            val scrollView = dialog.requireViewByIdCompat<NestedScrollView>(R.id.scrollView)
            val linearLayout = scrollView.getChildAt(0) as LinearLayout
            linearLayout.addView(binding.root)
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        notifyListenerOnce(FileOperationErrorAction.CANCELED, false)
        finish()
    }

    fun onFinish() {
        notifyListenerOnce(FileOperationErrorAction.CANCELED, false)
    }

    private fun notifyListenerOnce(action: FileOperationErrorAction, isAll: Boolean) {
        if (isListenerNotified) {
            return
        }
        args.listener(action, isAll)
        isListenerNotified = true
    }

    @Parcelize
    class Args(
        val title: CharSequence,
        val message: CharSequence,
        val readOnlyFileStore: @WriteWith<ParcelableParceler> PosixFileStore?,
        val showAll: Boolean,
        val positiveButtonText: CharSequence?,
        val negativeButtonText: CharSequence?,
        val neutralButtonText: CharSequence?,
        val listener: @WriteWith<ListenerParceler>() (FileOperationErrorAction, Boolean) -> Unit
    ) : ParcelableArgs {
        object ListenerParceler : Parceler<(FileOperationErrorAction, Boolean) -> Unit> {
            override fun create(parcel: Parcel): (FileOperationErrorAction, Boolean) -> Unit =
                parcel.readParcelable<RemoteCallback>()!!.let {
                    { action, isAll ->
                        it.sendResult(Bundle().putArgs(ListenerArgs(action, isAll)))
                    }
                }

            override fun ((FileOperationErrorAction, Boolean) -> Unit).write(parcel: Parcel, flags: Int) {
                parcel.writeParcelable(RemoteCallback {
                    val args = it.getArgs<ListenerArgs>()
                    this(args.action, args.isAll)
                }, flags)
            }

            @Parcelize
            private class ListenerArgs(
                val action: FileOperationErrorAction,
                val isAll: Boolean
            ) : ParcelableArgs
        }
    }

    @Parcelize
    private class State(
        val isAllChecked: Boolean
    ) : ParcelableState
}
