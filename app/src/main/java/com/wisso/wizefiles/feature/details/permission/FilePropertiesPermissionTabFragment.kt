// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.details.permission

import android.os.Bundle
import android.view.View
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.feature.details.FilePropertiesFileViewModel
import com.wisso.wizefiles.feature.details.FilePropertiesTabFragment
import com.wisso.wizefiles.provider.common.PosixFileAttributes
import com.wisso.wizefiles.provider.common.PosixPrincipal
import com.wisso.wizefiles.provider.common.readAttributes
import com.wisso.wizefiles.provider.common.toInt
import com.wisso.wizefiles.provider.common.toModeString
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.Stateful
import com.wisso.wizefiles.util.viewModels

class FilePropertiesPermissionTabFragment : FilePropertiesTabFragment() {
    private val viewModel by viewModels<FilePropertiesFileViewModel>({ requireParentFragment() })

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.fileLiveData.observe(viewLifecycleOwner) { onFileChanged(it) }
    }

    override fun refresh() {
        viewModel.reload()
    }

    private fun onFileChanged(stateful: Stateful<FileItem>) {
        bindView(stateful) { file ->
            val attributes = file.path.toLegacyPathOrNull()
                ?.let { path -> runCatching { path.readAttributes(PosixFileAttributes::class.java) }.getOrNull() }
                ?: return@bindView
            val owner = attributes.owner()
            addItemView(
                R.string.file_properties_permission_owner, getPrincipalText(owner), owner?.let {
                    {
                        SetOwnerDialogFragment.show(
                            file = file,
                            isDirectory = attributes.isDirectory,
                            ownerId = it.id,
                            fragment = this@FilePropertiesPermissionTabFragment
                        )
                    }
                }
            )
            val group = attributes.group()
            addItemView(
                R.string.file_properties_permission_group, getPrincipalText(group), group?.let {
                    {
                        SetGroupDialogFragment.show(
                            file = file,
                            isDirectory = attributes.isDirectory,
                            groupId = it.id,
                            fragment = this@FilePropertiesPermissionTabFragment
                        )
                    }
                }
            )
            val mode = attributes.mode()
            addItemView(
                R.string.file_properties_permission_mode, if (mode != null) {
                    getString(
                        R.string.file_properties_permission_mode_format, mode.toModeString(),
                        mode.toInt()
                    )
                } else {
                    getString(R.string.unknown)
                }, if (mode != null && !attributes.isSymbolicLink) {
                    {
                        SetModeDialogFragment.show(
                            file = file,
                            isDirectory = attributes.isDirectory,
                            mode = mode,
                            fragment = this@FilePropertiesPermissionTabFragment
                        )
                    }
                } else {
                    null
                }
            )
            val seLinuxContext = attributes.seLinuxContext()
            if (seLinuxContext != null) {
                addItemView(
                    R.string.file_properties_permission_selinux_context,
                    if (seLinuxContext.isNotEmpty()) {
                        seLinuxContext.toString()
                    } else {
                        getString(R.string.empty_placeholder)
                    }
                ) {
                    SetSeLinuxContextDialogFragment.show(
                        file = file,
                        isDirectory = attributes.isDirectory,
                        seLinuxContext = seLinuxContext.toString(),
                        fragment = this@FilePropertiesPermissionTabFragment
                    )
                }
            }
        }
    }

    private fun getPrincipalText(principal: PosixPrincipal?) =
        if (principal != null) {
            if (principal.name != null) {
                getString(
                    R.string.file_properties_permission_principal_format, principal.name,
                    principal.id
                )
            } else {
                principal.id.toString()
            }
        } else {
            getString(R.string.unknown)
        }

    companion object {
        fun isAvailable(file: FileItem): Boolean {
            val attributes = file.path.toLegacyPathOrNull()
                ?.let { path -> runCatching { path.readAttributes(PosixFileAttributes::class.java) }.getOrNull() }
                ?: return false
            return attributes.owner() != null
                || attributes.group() != null || attributes.mode() != null
                || attributes.seLinuxContext() != null
        }
    }
}
