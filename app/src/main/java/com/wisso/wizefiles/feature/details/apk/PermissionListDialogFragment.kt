package com.wisso.wizefiles.feature.details.apk

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.DialogPermissionListBinding
import com.wisso.wizefiles.util.Failure
import com.wisso.wizefiles.util.Loading
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.Stateful
import com.wisso.wizefiles.util.Success
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.fadeInUnsafe
import com.wisso.wizefiles.util.fadeOutUnsafe
import com.wisso.wizefiles.util.fadeToVisibilityUnsafe
import com.wisso.wizefiles.util.getQuantityString
import com.wisso.wizefiles.util.layoutInflater
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.show
import com.wisso.wizefiles.util.viewModels

class PermissionListDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private val viewModel by viewModels { { PermissionListViewModel(args.permissionNames) } }

    private lateinit var binding: DialogPermissionListBinding

    private lateinit var adapter: PermissionListAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext(), theme)
            .apply {
                val permissionsSize = args.permissionNames.size
                setTitle(
                    getQuantityString(
                        R.plurals.file_properties_apk_requested_permissions_positive_format,
                        permissionsSize, permissionsSize
                    )
                )

                binding = DialogPermissionListBinding.inflate(context.layoutInflater)
                binding.recyclerView.layoutManager = LinearLayoutManager(context)
                adapter = PermissionListAdapter()
                binding.recyclerView.adapter = adapter
                setView(binding.root)

                viewModel.permissionListLiveData.observe(this@PermissionListDialogFragment) {
                    onPermissionListChanged(it)
                }
            }
            .setPositiveButton(android.R.string.ok, null)
            .create()

    private fun onPermissionListChanged(stateful: Stateful<List<PermissionItem>>) {
        when (stateful) {
            is Loading -> {
                binding.progress.fadeInUnsafe()
                binding.errorText.fadeOutUnsafe()
                binding.emptyView.fadeOutUnsafe()
                adapter.clear()
            }
            is Failure -> {
                binding.progress.fadeOutUnsafe()
                binding.errorText.fadeInUnsafe()
                binding.errorText.text = stateful.throwable.toString()
                binding.emptyView.fadeOutUnsafe()
                adapter.clear()
            }
            is Success -> {
                binding.progress.fadeOutUnsafe()
                binding.errorText.fadeOutUnsafe()
                binding.emptyView.fadeToVisibilityUnsafe(stateful.value.isEmpty())
                adapter.replace(stateful.value)
            }
        }
    }

    companion object {
        fun show(permissionNames: Array<String>, fragment: Fragment) {
            PermissionListDialogFragment().putArgs(Args(permissionNames)).show(fragment)
        }
    }

    @Parcelize
    class Args(val permissionNames: Array<String>) : ParcelableArgs
}
