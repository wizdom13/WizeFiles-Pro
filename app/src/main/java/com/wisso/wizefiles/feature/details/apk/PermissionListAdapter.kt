// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.details.apk

import android.content.pm.PermissionInfo
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.wisso.wizefiles.core.app.clipboardManager
import com.wisso.wizefiles.core.android.compat.protectionCompat
import com.wisso.wizefiles.databinding.ItemPermissionBinding
import com.wisso.wizefiles.ui.SimpleAdapter
import com.wisso.wizefiles.util.copyText
import com.wisso.wizefiles.util.isBold
import com.wisso.wizefiles.util.layoutInflater
import java.util.Locale

class PermissionListAdapter : SimpleAdapter<PermissionItem, PermissionListAdapter.ViewHolder>() {
    override val hasStableIds: Boolean
        get() = true

    override fun getItemId(position: Int): Long = getItem(position).name.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemPermissionBinding.inflate(parent.context.layoutInflater, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val binding = holder.binding
        val permission = getItem(position)
        val name = permission.name
        binding.root.setOnLongClickListener {
            clipboardManager.copyText(name, binding.root.context)
            true
        }
        val label = permission.label
        binding.labelText.text = label?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } ?: name
        binding.labelText.isBold = (permission.permissionInfo?.protectionCompat
            == PermissionInfo.PROTECTION_DANGEROUS)
        binding.nameText.isVisible = label != null
        binding.nameText.text = name
        binding.descriptionText.text = permission.description
    }

    class ViewHolder(val binding: ItemPermissionBinding) : RecyclerView.ViewHolder(binding.root)
}
