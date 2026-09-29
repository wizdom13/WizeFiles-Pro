// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.format.Formatter
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import com.wisso.wizefiles.storage.path.toAppPath

class SyncthingVersionsActivity : AppCompatActivity() {
    private val profileId by lazy { requireNotNull(intent.getStringExtra("profile")) }
    private lateinit var ui: SyncthingUi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = SyncthingUi(this, getString(R.string.syncthing_recovery))
        refresh()
    }
    private fun refresh() {
        val profile = SyncRepository.profile(profileId) ?: return finish()
        ui.clear()
        ui.text(getString(R.string.syncthing_recovery_help))
        ui.button(R.string.syncthing_retention) { retention(profile) }
        ui.button(R.string.refresh, ::refresh)
        ui.action(idle = false, block = {
            val root = SyncthingLocalFolder.resolve(profile.sourceUri)
            val conflicts = SyncthingVersionPolicy.conflicts(root)
            val versions = if (SyncthingVersionPolicy.keep(profile.protectionJson) > 0)
                SyncthingVersionAccess(this).list(profile) else emptyList()
            conflicts to versions
        }) { (conflicts, versions) ->
            ui.text(getString(R.string.syncthing_conflict_files), true)
            conflicts.names.forEach { ui.text(it) }
            if (conflicts.truncated) ui.text(getString(R.string.syncthing_conflicts_partial))
            ui.button(R.string.syncthing_open_folder) {
                startActivity(FileListActivity.createViewIntent(java.nio.file.Paths.get(java.net.URI(profile.sourceUri)).toAppPath()))
            }
            ui.text(getString(R.string.syncthing_saved_versions), true)
            versions.groupBy { it.path }.forEach { (path, entries) ->
                ui.text(path, true)
                ui.button(R.string.syncthing_restore_version) {
                    MaterialAlertDialogBuilder(this).setTitle(path)
                        .setItems(entries.map { "${it.time} · ${Formatter.formatFileSize(this, it.size)}" }.toTypedArray()) { _, index ->
                            val chosen = entries[index]
                            MaterialAlertDialogBuilder(this).setTitle(R.string.syncthing_restore_version)
                                .setMessage(getString(R.string.syncthing_restore_confirm, chosen.path, chosen.time))
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(R.string.syncthing_restore_version) { _, _ ->
                                    ui.action(idle = false, block = {
                                        SyncthingVersionAccess(this).restore(requireNotNull(SyncRepository.profile(profileId)), chosen)
                                    }) { refresh() }
                                }.show()
                        }.show()
                }
            }
        }
    }
    private fun retention(profile: SyncProfile) {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val count = ui.field(form, R.string.syncthing_retention, SyncthingVersionPolicy.keep(profile.protectionJson).toString())
        count.inputType = InputType.TYPE_CLASS_NUMBER
        val dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.syncthing_retention).setView(form)
            .setMessage(R.string.syncthing_retention_help).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save, null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val value = count.text.toString().toIntOrNull()
                if (value == null || value !in 0..100) { count.error = getString(R.string.syncthing_retention); return@setOnClickListener }
                ui.action(block = {
                    val latest = requireNotNull(SyncRepository.profile(profileId))
                    SyncRepository.saveProfile(latest.copy(protectionJson = SyncthingVersionPolicy.encode(latest.protectionJson, value)))
                }) { dialog.dismiss(); refresh() }
            }
        }
        dialog.show()
    }
    companion object {
        fun createIntent(context: Context, profileId: String) = Intent(context, SyncthingVersionsActivity::class.java)
            .putExtra("profile", profileId)
    }
}
