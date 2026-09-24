// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.content.Context
import android.content.Intent
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.feature.audioplayer.SetRingtoneActivity
import com.wisso.wizefiles.feature.apksigning.AabSignVerifyActivity
import com.wisso.wizefiles.feature.apksigning.ApkSignVerifyActivity
import com.wisso.wizefiles.feature.apksigning.ApksSignVerifyActivity
import com.wisso.wizefiles.feature.apksigning.ApkmImportActivity
import com.wisso.wizefiles.feature.apksigning.XapkSignVerifyActivity

internal object BrowserPackageSelectionActionHandler {
    fun handle(
        itemId: Int,
        file: FileItem?,
        context: Context,
        ensurePackageSigningAccess: () -> Boolean,
        launch: (Intent) -> Unit,
        clearSelection: () -> Unit
    ): Boolean? {
        if (itemId == R.id.action_set_ringtone) {
            val selected = file ?: return false
            launch(
                SetRingtoneActivity.createIntent(
                    context,
                    selected.path,
                    selected.mimeType,
                    selected.path.name
                )
            )
            clearSelection()
            return true
        }
        val action = when (itemId) {
            R.id.action_sign_apk -> PackageAction.APK_SIGN
            R.id.action_verify_apk -> PackageAction.APK_VERIFY
            R.id.action_sign_aab -> PackageAction.AAB_SIGN
            R.id.action_verify_aab -> PackageAction.AAB_VERIFY
            R.id.action_sign_apks -> PackageAction.APKS_SIGN
            R.id.action_verify_apks -> PackageAction.APKS_VERIFY
            R.id.action_sign_xapk -> PackageAction.XAPK_SIGN
            R.id.action_verify_xapk -> PackageAction.XAPK_VERIFY
            R.id.action_import_apkm -> PackageAction.APKM_IMPORT
            else -> return null
        }
        if (action.requiresPro && !ensurePackageSigningAccess()) return true
        val selected = file ?: return false
        val intent = when (action) {
            PackageAction.APK_SIGN -> ApkSignVerifyActivity.createSignIntent(context, selected.path)
            PackageAction.APK_VERIFY ->
                ApkSignVerifyActivity.createVerifyIntent(context, selected.path)
            PackageAction.AAB_SIGN -> AabSignVerifyActivity.createSignIntent(context, selected.path)
            PackageAction.AAB_VERIFY ->
                AabSignVerifyActivity.createVerifyIntent(context, selected.path)
            PackageAction.APKS_SIGN ->
                ApksSignVerifyActivity.createSignIntent(context, selected.path)
            PackageAction.APKS_VERIFY ->
                ApksSignVerifyActivity.createVerifyIntent(context, selected.path)
            PackageAction.XAPK_SIGN ->
                XapkSignVerifyActivity.createSignIntent(context, selected.path)
            PackageAction.XAPK_VERIFY ->
                XapkSignVerifyActivity.createVerifyIntent(context, selected.path)
            PackageAction.APKM_IMPORT -> ApkmImportActivity.createIntent(context, selected.path)
        }
        launch(intent)
        clearSelection()
        return true
    }

    private enum class PackageAction(val requiresPro: Boolean) {
        APK_SIGN(true),
        APK_VERIFY(false),
        AAB_SIGN(true),
        AAB_VERIFY(false),
        APKS_SIGN(true),
        APKS_VERIFY(false),
        XAPK_SIGN(true),
        XAPK_VERIFY(false),
        APKM_IMPORT(true)
    }
}
