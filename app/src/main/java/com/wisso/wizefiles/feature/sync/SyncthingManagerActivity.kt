// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import com.wisso.wizefiles.R
import org.json.JSONObject

class SyncthingManagerActivity : AppCompatActivity() {
    private lateinit var ui: SyncthingUi
    private val configuration get() = SyncthingSettings.configuration
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = SyncthingUi(this, getString(R.string.syncthing_manage))
    }
    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        ui.action(idle = false, block = {
            SyncthingSettings.migrate()
            val runtime = SyncthingRuntime.get(this)
            val connections = if (runtime.isRunning()) runCatching {
                JSONObject(runtime.request("GET", "/rest/system/connections", null)).optJSONObject("connections")
            }.getOrNull() else null
            Triple(configuration.devices(), SyncRepository.profiles().filter {
                SyncBackendRouter.kind(it) == SyncBackendKind.SYNCTHING
            }, connections)
        }) { (devices, profiles, connections) ->
            ui.clear()
            ui.button(R.string.syncthing_this_device, ::copyOwnId)
            ui.button(R.string.syncthing_add_device) { editDevice(null) }
            ui.button(R.string.syncthing_setup) {
                startActivity(SyncProfilesActivity.createIntent(this).putExtra(SyncProfilesActivity.EXTRA_ADD_SYNCTHING, true))
            }
            ui.button(R.string.syncthing_backup_title) { startActivity(SyncthingBackupActivity.createIntent(this)) }
            ui.button(R.string.refresh, ::refresh)
            ui.text(getString(R.string.syncthing_devices), true)
            devices.forEach { device ->
                val state = when {
                    connections == null -> R.string.syncthing_status_stopped
                    connections.optJSONObject(device.id)?.optBoolean("connected") == true -> R.string.syncthing_online
                    else -> R.string.syncthing_offline
                }
                ui.text("${device.name.ifBlank { device.id }}\n${device.id}\n${getString(state)}")
                ui.button(R.string.edit) { editDevice(device) }
                ui.button(R.string.delete) {
                    MaterialAlertDialogBuilder(this).setTitle(R.string.delete)
                        .setMessage(R.string.syncthing_remove_device_help)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            ui.action(block = { configuration.removeDevice(device.id) }) { refresh() }
                        }.show()
                }
            }
            ui.text(getString(R.string.syncthing_folders), true)
            profiles.forEach { profile ->
                val paused = configuration.isPaused(profile.id)
                val peers = configuration.peers(profile.id)
                ui.text(profile.name, true)
                ui.text(profile.sourceUri + "\n" + SyncthingProfilePolicy.validate(profile).folderId + "\n" +
                    peers.joinToString { it.name.ifBlank { it.id.take(7) } }.ifEmpty { getString(R.string.syncthing_no_peers) })
                ui.button(R.string.sync_run_now) {
                    if (!paused && peers.isNotEmpty()) SyncthingRunWorker.enqueue(this, profile.id,
                        SyncRepository.runs(profile.id).firstOrNull { it.state == SyncRunState.PAUSED }?.id)
                }.isEnabled = !paused && peers.isNotEmpty()
                ui.button(R.string.edit) {
                    startActivity(SyncProfilesActivity.createIntent(this)
                        .putExtra(SyncProfilesActivity.EXTRA_EDIT_PROFILE, profile.id))
                }
                ui.button(R.string.syncthing_status) {
                    startActivity(SyncthingStatusActivity.createIntent(this, profile.id))
                }
                ui.button(R.string.syncthing_sharing) { editShares(profile, devices) }
                ui.button(if (paused) R.string.syncthing_enable_folder else R.string.syncthing_pause_folder) {
                    ui.action(block = { configuration.setPaused(profile.id, !paused) }) { refresh() }
                }
            }
        }
    }

    private fun copyOwnId() {
        ui.action(block = {
            val runtime = SyncthingRuntime.get(this).acquire()
            try { SyncthingRestEngine(runtime).deviceId().also(configuration::rememberOwnId) }
            finally { runtime.release() }
        }) { id ->
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Syncthing", id))
            MaterialAlertDialogBuilder(this).setMessage(id).setPositiveButton(android.R.string.ok, null).show()
        }
    }

    private fun editDevice(existing: SyncthingDevice?) {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val id = ui.field(form, R.string.syncthing_device_id, existing?.id.orEmpty())
        id.isEnabled = existing == null
        val name = ui.field(form, R.string.syncthing_device_name, existing?.name.orEmpty())
        val addresses = ui.field(form, R.string.syncthing_addresses, existing?.addresses?.joinToString(", ") ?: "dynamic")
        val dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.syncthing_add_device).setView(form)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.save, null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val enteredId = id.text.toString().trim().uppercase(java.util.Locale.ROOT)
                val enteredName = name.text.toString().trim()
                val enteredAddresses = addresses.text.toString().split(',').map(String::trim).filter(String::isNotEmpty)
                ui.action(block = {
                    val runtime = SyncthingRuntime.get(this).acquire()
                    try {
                        val canonical = JSONObject(runtime.request("GET", "/rest/svc/deviceid?id=" +
                            SyncthingHttp.component(enteredId), null)).getString("id")
                        require(canonical != SyncthingRestEngine(runtime).deviceId())
                        configuration.saveDevice(SyncthingDevice(canonical, enteredName, enteredAddresses))
                    } finally { runtime.release() }
                }) { dialog.dismiss(); refresh() }
            }
        }
        dialog.show()
    }

    private fun editShares(profile: SyncProfile, devices: List<SyncthingDevice>) {
        val selected = configuration.peers(profile.id).map { it.id }.toMutableSet()
        MaterialAlertDialogBuilder(this).setTitle(R.string.syncthing_sharing)
            .setMultiChoiceItems(devices.map { it.name.ifBlank { it.id } }.toTypedArray(),
                devices.map { it.id in selected }.toBooleanArray()) { _, index, checked ->
                if (checked) selected.add(devices[index].id) else selected.remove(devices[index].id)
            }.setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                ui.action(block = { configuration.setPeers(profile.id, selected.toList()) }) { refresh() }
            }.show()
    }

    companion object {
        fun createIntent(context: android.content.Context) = Intent(context, SyncthingManagerActivity::class.java)
    }
}
