// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.about

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.ItemFaqBinding

class FaqAdapter(
    private val items: List<FaqItem>
) : RecyclerView.Adapter<FaqAdapter.ViewHolder>() {
    private val expandedPositions = linkedSetOf(0)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFaqBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], expandedPositions.contains(position))
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemFaqBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) {
                    return@setOnClickListener
                }
                if (!expandedPositions.add(position)) {
                    expandedPositions.remove(position)
                }
                notifyItemChanged(position)
            }
        }

        fun bind(item: FaqItem, isExpanded: Boolean) {
            binding.questionText.text = item.question
            binding.answerText.text = item.answer
            binding.answerText.isVisible = isExpanded
            binding.expandIcon.setImageResource(
                if (isExpanded) {
                    R.drawable.ic_bs_chevron_up_control_normal_24dp
                } else {
                    R.drawable.ic_bs_chevron_down_control_normal_24dp
                }
            )
            binding.faqCard.strokeWidth = if (item.isHighlighted) {
                (binding.root.context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            } else {
                0
            }
        }
    }
}
