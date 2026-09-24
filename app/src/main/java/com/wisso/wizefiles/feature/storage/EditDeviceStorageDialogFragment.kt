package com.wisso.wizefiles.storage

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.DialogEditDeviceStorageBinding
import com.wisso.wizefiles.util.showImeWithResize
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.finish
import com.wisso.wizefiles.util.layoutInflater
import com.wisso.wizefiles.util.setTextWithSelection

class EditDeviceStorageDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private lateinit var binding: DialogEditDeviceStorageBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val deviceStorage = args.deviceStorage
        return MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(R.string.storage_edit_device_storage_title)
            .apply {
                binding = DialogEditDeviceStorageBinding.inflate(context.layoutInflater)
                binding.nameLayout.placeholderText = deviceStorage.getDefaultName(
                    binding.nameLayout.context
                )
                if (savedInstanceState == null) {
                    binding.nameEdit.setTextWithSelection(
                        deviceStorage.getName(binding.nameEdit.context)
                    )
                }
                binding.pathText.setText(deviceStorage.linuxPath)
                setView(binding.root)
            }
            .setPositiveButton(android.R.string.ok) { _, _ -> save() }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
            .setNeutralButton(
                if (deviceStorage.isVisible) R.string.hide else R.string.show
            ) { _, _ -> toggleVisibility() }
            .create()
            .apply {
                window!!.showImeWithResize()
            }
    }

    private fun save() {
        val customName = binding.nameEdit.text.toString()
            .takeIf { it.isNotEmpty() && it != binding.nameLayout.placeholderText }
        val deviceStorage = args.deviceStorage.copy_(customName = customName)
        Storages.replace(deviceStorage)
        finish()
    }

    private fun toggleVisibility() {
        val deviceStorage = args.deviceStorage.let { it.copy_(isVisible = !it.isVisible) }
        Storages.replace(deviceStorage)
        finish()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        finish()
    }

    @Parcelize
    class Args(val deviceStorage: DeviceStorage) : ParcelableArgs
}
