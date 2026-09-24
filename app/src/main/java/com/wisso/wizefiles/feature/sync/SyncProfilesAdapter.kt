// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.wisso.wizefiles.databinding.ItemSyncProfileBinding
import com.wisso.wizefiles.feature.transfer.formatTransferPath
import java.text.DateFormat
import java.util.Date

internal class SyncProfilesAdapter(
    private val onRun: (SyncProfile) -> Unit,
    private val onEdit: (SyncProfile) -> Unit,
    private val onToggle: (SyncProfile) -> Unit,
    private val onHistory: (SyncProfile) -> Unit,
    private val onDelete: (SyncProfile) -> Unit
) : RecyclerView.Adapter<SyncProfilesAdapter.Holder>() {
    private var profiles = emptyList<SyncProfile>()

    fun submit(value: List<SyncProfile>) {
        profiles = value
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemSyncProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = profiles.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(profiles[position])

    private fun modeLabel(context: Context, mode: SyncMode): String = context.getString(
        when (mode) {
            SyncMode.UPDATE_DESTINATION -> com.wisso.wizefiles.R.string.sync_mode_update
            SyncMode.MIRROR -> com.wisso.wizefiles.R.string.sync_mode_mirror
            SyncMode.TWO_WAY -> com.wisso.wizefiles.R.string.sync_mode_two_way
            SyncMode.MOVE_SOURCE -> com.wisso.wizefiles.R.string.sync_mode_move
        }
    )

    private fun scheduleLabel(context: Context, schedule: SyncSchedule): String = when (schedule.type) {
        SyncScheduleType.MANUAL -> context.getString(
            com.wisso.wizefiles.R.string.sync_schedule_manual
        )
        SyncScheduleType.INTERVAL -> context.getString(
            com.wisso.wizefiles.R.string.sync_schedule_every_minutes,
            schedule.intervalMinutes
        )
        SyncScheduleType.DAILY -> context.getString(
            com.wisso.wizefiles.R.string.sync_schedule_daily,
            "%02d:%02d".format(schedule.localTime.hour, schedule.localTime.minute)
        )
        SyncScheduleType.WEEKLY -> context.getString(
            com.wisso.wizefiles.R.string.sync_schedule_weekly,
            schedule.daysOfWeek.firstOrNull()?.name
                ?.lowercase()?.replaceFirstChar(Char::uppercase)
                ?: context.getString(com.wisso.wizefiles.R.string.unknown),
            "%02d:%02d".format(schedule.localTime.hour, schedule.localTime.minute)
        )
    }

    inner class Holder(private val binding: ItemSyncProfileBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(profile: SyncProfile) {
            val context = binding.root.context
            val latest = SyncRepository.runs(profile.id).firstOrNull()
            binding.name.text = profile.name
            binding.endpoints.text = "${formatTransferPath(context, profile.sourceUri)} → " +
                formatTransferPath(context, profile.destinationUri)
            val schedule = SyncScheduleCodec.decode(profile.scheduleJson)
            binding.status.text = buildList {
                add(modeLabel(context, profile.mode))
                add(scheduleLabel(context, schedule))
                latest?.let {
                    add(it.state.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase))
                }
                if (schedule.type != SyncScheduleType.MANUAL && !profile.enabled) {
                    add(context.getString(com.wisso.wizefiles.R.string.sync_schedule_paused))
                } else if (profile.enabled && profile.nextRunAtMillis > 0) {
                    add(
                        context.getString(
                            com.wisso.wizefiles.R.string.sync_scheduled_around,
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(profile.nextRunAtMillis))
                        )
                    )
                }
            }.joinToString(" • ")
            binding.runNow.setOnClickListener { onRun(profile) }
            binding.edit.setOnClickListener { onEdit(profile) }
            binding.toggleSchedule.isVisible = schedule.type != SyncScheduleType.MANUAL
            binding.toggleSchedule.text = context.getString(
                if (profile.enabled) {
                    com.wisso.wizefiles.R.string.sync_pause_schedule
                } else {
                    com.wisso.wizefiles.R.string.sync_resume_schedule
                }
            )
            binding.toggleSchedule.setOnClickListener { onToggle(profile) }
            binding.moreActions.setOnClickListener {
                showMoreMenu(
                    profile,
                    hasHistory = latest?.transferOperationId?.isNotBlank() == true
                )
            }
            binding.root.setOnLongClickListener {
                onDelete(profile)
                true
            }
        }

        private fun showMoreMenu(profile: SyncProfile, hasHistory: Boolean) {
            val context = binding.root.context
            PopupMenu(context, binding.moreActions).apply {
                menu.add(
                    Menu.NONE,
                    MENU_HISTORY,
                    Menu.NONE,
                    com.wisso.wizefiles.R.string.sync_history
                ).isEnabled = hasHistory
                val deleteTitle = SpannableString(
                    context.getString(com.wisso.wizefiles.R.string.delete)
                ).apply {
                    setSpan(
                        ForegroundColorSpan(
                            MaterialColors.getColor(
                                binding.root,
                                com.google.android.material.R.attr.colorError
                            )
                        ),
                        0,
                        length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                menu.add(Menu.NONE, MENU_DELETE, Menu.NONE, deleteTitle)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        MENU_HISTORY -> {
                            onHistory(profile)
                            true
                        }
                        MENU_DELETE -> {
                            onDelete(profile)
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
        }
    }

    private companion object {
        const val MENU_HISTORY = 1
        const val MENU_DELETE = 2
    }
}
