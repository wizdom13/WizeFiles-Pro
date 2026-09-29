// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.storage.path.toUriString
import java.nio.file.Files
import java.nio.file.StandardOpenOption

internal class SyncthingBackupState : ViewModel() {
    var model: SyncthingImportModel? = null
    val paths = linkedMapOf<String, String>()
    var folder: String? = null
    var preserveIdentity = false
    var internet = false
    var encryptedExport: ByteArray? = null
    fun clearImport() { model?.identity?.privateKey?.fill(0); model = null; paths.clear(); preserveIdentity = false; internet = false }
    override fun onCleared() { clearImport(); encryptedExport?.fill(0) }
}

class SyncthingBackupActivity : AppCompatActivity() {
    private lateinit var ui: SyncthingUi
    private val state by viewModels<SyncthingBackupState>()
    private val open = registerForActivityResult(FileListActivity.OpenFileContract()) { path ->
        if (path != null) ui.action(idle = false, block = {
            val file = requireNotNull(path.toLegacyPathOrNull()) { "Cannot read the selected backup" }
            Files.newInputStream(file).use(SyncthingBackupCodec::read)
        }) { bytes ->
            if (SyncthingBackupCodec.encrypted(bytes)) password(false) { secret -> parse(bytes, secret) }
            else parse(bytes, CharArray(0))
        }
    }
    private val create = registerForActivityResult(FileListActivity.CreateFileContract()) { path ->
        val bytes = state.encryptedExport
        if (path == null || bytes == null) { state.encryptedExport?.fill(0); state.encryptedExport = null }
        else ui.action(idle = false, block = {
            try {
                val file = requireNotNull(path.toLegacyPathOrNull()) { "Cannot write the selected backup" }
                Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE).use { it.write(bytes) }
                require(Files.newInputStream(file).use(SyncthingBackupCodec::read).contentEquals(bytes)) {
                    "Backup verification failed"
                }
            } finally { bytes.fill(0); state.encryptedExport = null }
        }) { Toast.makeText(this, R.string.syncthing_backup_saved, Toast.LENGTH_LONG).show() }
    }
    private val directory = registerForActivityResult(FileListActivity.OpenDirectoryContract()) { path ->
        val folder = state.folder
        if (path != null && folder != null) ui.action(idle = false, block = {
            SyncthingLocalFolder.resolve(path.toUriString()).path
        }) { local -> state.paths[folder] = local; preview() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = SyncthingUi(this, getString(R.string.syncthing_backup_title))
        if (state.model == null) home() else preview()
    }
    private fun home() {
        ui.clear()
        ui.text(getString(R.string.syncthing_backup_help))
        ui.button(R.string.syncthing_import) { open.launch(listOf(MimeType.ANY)) }
        ui.button(R.string.syncthing_export) {
            password(true) { secret ->
                ui.action(idle = false, block = {
                    try { SyncthingBackup.export(this, secret) } finally { secret.fill('\u0000') }
                }) { encrypted ->
                    state.encryptedExport = encrypted
                    create.launch(Triple(MimeType.GENERIC, "WizeFiles-Syncthing-${java.time.LocalDate.now()}.wfsync", null))
                }
            }
        }
    }
    private fun parse(bytes: ByteArray, password: CharArray) {
        ui.action(idle = false, block = {
            try { SyncthingImportParser.parse(bytes, password) }
            finally { bytes.fill(0); password.fill('\u0000') }
        }) { model ->
            state.clearImport()
            state.model = model
            model.folders.forEach { state.paths[it.id] = it.path }
            preview()
        }
    }
    private fun password(export: Boolean, action: (CharArray) -> Unit) {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val first = ui.field(form, R.string.syncthing_backup_password).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSaveEnabled = false
        }
        val second = if (export) ui.field(form, R.string.syncthing_confirm_password).apply {
            inputType = first.inputType; isSaveEnabled = false
        } else null
        val dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.syncthing_backup_password)
            .setMessage(if (export) R.string.syncthing_password_help else R.string.syncthing_decrypt_help).setView(form)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val value = first.text.toString()
                if (export && (value.length < 12 || value != second?.text.toString())) {
                    first.error = getString(R.string.syncthing_password_help); return@setOnClickListener
                }
                first.text?.clear(); second?.text?.clear(); dialog.dismiss()
                action(value.toCharArray())
            }
        }
        dialog.show()
    }
    private fun preview() {
        val model = state.model ?: return home()
        ui.clear()
        ui.text(getString(R.string.syncthing_import_preview, model.devices.count { it.id != model.originalId }, model.folders.size), true)
        ui.text(getString(R.string.syncthing_import_help))
        ui.text(getString(R.string.syncthing_original_id, model.originalId.ifBlank { "—" }))
        if (model.originalId.isBlank()) ui.button(R.string.syncthing_choose_original) {
            MaterialAlertDialogBuilder(this).setTitle(R.string.syncthing_choose_original)
                .setItems(model.devices.map { "${it.name}\n${it.id}" }.toTypedArray()) { _, index ->
                    state.model = model.copy(originalId = model.devices[index].id); preview()
                }.show()
        }
        ui.body.addView(SwitchMaterial(this).apply {
            setText(R.string.syncthing_preserve_identity)
            isEnabled = model.identity != null
            isChecked = state.preserveIdentity
            setOnCheckedChangeListener { _, checked -> state.preserveIdentity = checked }
        })
        ui.text(getString(if (model.identity == null) R.string.syncthing_missing_identity else R.string.syncthing_identity_warning))
        ui.body.addView(SwitchMaterial(this).apply {
            setText(R.string.syncthing_public_network)
            isChecked = state.internet
            setOnCheckedChangeListener { _, checked -> state.internet = checked }
        })
        model.folders.forEach { folder ->
            ui.text("${folder.name} · ${folder.id}", true)
            ui.text(getString(if (folder.mode == SyncMode.TWO_WAY) R.string.sync_mode_two_way else R.string.syncthing_send_only))
            ui.text(state.paths[folder.id].orEmpty())
            ui.button(R.string.syncthing_map_folder) {
                state.folder = folder.id
                directory.launch(FileListActivity.OpenDirectoryRequest(title = folder.name,
                    confirmationLabel = getString(R.string.file_list_use_current_directory_as_source)))
            }
        }
        ui.button(R.string.syncthing_import) {
            MaterialAlertDialogBuilder(this).setTitle(R.string.syncthing_import)
                .setMessage(R.string.syncthing_replace_confirm).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.syncthing_import) { _, _ ->
                    // Capture the reviewed choices before leaving the UI thread.
                    val paths = state.paths.toMap()
                    val preserve = state.preserveIdentity
                    val internet = state.internet
                    ui.action(idle = false, block = { SyncthingMigration.apply(this, model, paths, preserve, internet) }) { id ->
                        state.clearImport(); home()
                        MaterialAlertDialogBuilder(this).setMessage(getString(R.string.syncthing_import_complete, id))
                            .setPositiveButton(android.R.string.ok, null).show()
                    }
                }.show()
        }.isEnabled = model.originalId.isNotBlank()
        ui.button(android.R.string.cancel) { state.clearImport(); home() }
    }
    companion object {
        fun createIntent(context: Context) = Intent(context, SyncthingBackupActivity::class.java)
    }
}
