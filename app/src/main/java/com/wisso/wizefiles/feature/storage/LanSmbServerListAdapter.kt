// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wisso.wizefiles.databinding.ItemLanSmbServerBinding
import com.wisso.wizefiles.ui.SimpleAdapter
import com.wisso.wizefiles.util.layoutInflater

class LanSmbServerListAdapter(
    val listener: (LanSmbServer) -> Unit
) : SimpleAdapter<LanSmbServer, LanSmbServerListAdapter.ViewHolder>() {
    override val hasStableIds: Boolean
        get() = true

    override fun getItemId(position: Int): Long = getItem(position).hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemLanSmbServerBinding.inflate(parent.context.layoutInflater, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val server = getItem(position)
        val binding = holder.binding
        binding.itemLayout.setOnClickListener { listener(server) }
        binding.hostText.text = server.host
        binding.addressText.text = server.address.hostAddress
    }

    class ViewHolder(val binding: ItemLanSmbServerBinding) : RecyclerView.ViewHolder(binding.root)
}
