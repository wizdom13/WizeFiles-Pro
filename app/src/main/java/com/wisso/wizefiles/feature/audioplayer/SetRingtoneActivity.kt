// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.audioplayer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.asMimeTypeOrNull
import com.wisso.wizefiles.feature.internalviewer.InternalOpenPolicy
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.extraPath
import com.wisso.wizefiles.util.showToast
import kotlinx.coroutines.launch

/** Transparent coordinator for the special Android permissions required by Set as ringtone. */
class SetRingtoneActivity : BaseThemedActivity() {
    private var setting = false

    private val writeSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (AudioRingtoneSetter.canWriteSystemSettings(this)) {
            continueFlow()
        } else {
            showToast(R.string.audio_player_ringtone_permission_required)
            finish()
        }
    }

    private val legacyStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            continueFlow()
        } else {
            showToast(R.string.audio_player_ringtone_permission_required)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            continueFlow()
        }
    }

    private fun continueFlow() {
        if (setting) return
        val request = resolveRequest() ?: run {
            showToast(R.string.audio_player_ringtone_failed)
            finish()
            return
        }
        if (!AudioRingtoneSetter.canWriteSystemSettings(this)) {
            val launched = runCatching {
                writeSettingsLauncher.launch(AudioRingtoneSetter.createWriteSettingsIntent(this))
            }.isSuccess
            if (!launched) {
                showToast(R.string.audio_player_ringtone_failed)
                finish()
            }
            return
        }
        if (
            AudioRingtoneSetter.needsLegacyStoragePermission() &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        setting = true
        lifecycleScope.launch {
            val success = AudioRingtoneSetter.setAsDefaultRingtone(
                this@SetRingtoneActivity,
                request.source,
                request.displayName,
                request.mimeType
            )
            showToast(
                if (success) R.string.audio_player_ringtone_set
                else R.string.audio_player_ringtone_failed
            )
            finish()
        }
    }

    private fun resolveRequest(): Request? {
        val appPath = intent.extraPath ?: return null
        val source = runCatching { appPath.toLegacyPathOrNull() }.getOrNull() ?: return null
        val mimeType = intent.type?.asMimeTypeOrNull() ?: return null
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: appPath.name
        if (
            InternalOpenPolicy.targetAfterExtraction(mimeType, displayName) !=
            InternalOpenPolicy.Target.AUDIO_PLAYER
        ) {
            return null
        }
        return Request(source, displayName, mimeType)
    }

    private data class Request(
        val source: java.nio.file.Path,
        val displayName: String,
        val mimeType: MimeType
    )

    companion object {
        private const val EXTRA_DISPLAY_NAME =
            "com.wisso.wizefiles.audioplayer.extra.RINGTONE_DISPLAY_NAME"

        fun createIntent(
            context: Context,
            path: AppPath,
            mimeType: MimeType,
            displayName: String = path.name
        ): Intent = Intent(context, SetRingtoneActivity::class.java)
            .setType(mimeType.value)
            .putExtra(EXTRA_DISPLAY_NAME, displayName)
            .apply { extraPath = path }
    }
}
