// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.os.Bundle
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.feature.audioplayer.AudioPlayerActivity
import com.wisso.wizefiles.feature.internalviewer.InternalOpenPolicy
import com.wisso.wizefiles.feature.mediapreview.MediaPreviewActivity
import com.wisso.wizefiles.feature.packageinstaller.AndroidPackageInstallerInput
import com.wisso.wizefiles.feature.packageinstaller.PackageInstallerActivity
import com.wisso.wizefiles.settings.SettingsActivity
import com.wisso.wizefiles.settings.SettingsBackupViewIntent
import com.wisso.wizefiles.viewer.common.ExternalIntentValidator
import com.wisso.wizefiles.viewer.text.TextEditorActivity
import java.util.Locale

internal const val EXTRA_EXTERNAL_DISPLAY_NAME =
    "com.wisso.wizefiles.filebrowser.extra.EXTERNAL_DISPLAY_NAME"

class ExternalViewRouterActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            route(intent)
        }
        finish()
    }

    private fun route(sourceIntent: Intent) {
        val uri = sourceIntent.data
        if (sourceIntent.action != Intent.ACTION_VIEW ||
            uri == null ||
            !ExternalIntentValidator.isSupportedLocalExternalUri(uri)
        ) {
            return
        }

        val resolvedDisplayName = runCatching {
            SettingsBackupViewIntent.resolveDisplayName(this, uri)
        }.getOrNull()
        val externalDisplayName = resolvedDisplayName?.takeIf { it.isNotBlank() }
        val sourceDisplayName = externalDisplayName ?: uri.lastPathSegment
        val isBackup = SettingsBackupViewIntent.findBackupUri(sourceIntent) {
            resolvedDisplayName
        } != null
        val resolvedMimeType = if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching { contentResolver.getType(uri) }.getOrNull()
        } else {
            null
        }
        val target = ExternalViewRouteSelector.select(
            sourceIntent.type,
            resolvedMimeType,
            isBackup,
            sourceDisplayName
        )
        if (target != ExternalViewTarget.PACKAGE_INSTALLER &&
            !ExternalIntentValidator.isTrustedExternalUri(uri)
        ) {
            return
        }
        val targetIntent = when (target) {
            ExternalViewTarget.SETTINGS_RESTORE ->
                Intent(sourceIntent).setClass(this, SettingsActivity::class.java)
            ExternalViewTarget.TEXT_EDITOR ->
                Intent(sourceIntent).setClass(this, TextEditorActivity::class.java)
            ExternalViewTarget.AUDIO_PLAYER ->
                Intent(sourceIntent).setClass(this, AudioPlayerActivity::class.java).apply {
                    setDataAndType(
                        uri,
                        ExternalViewRouteSelector.mediaLaunchMimeType(
                            sourceIntent.type,
                            resolvedMimeType,
                            target
                        )
                    )
                    externalDisplayName?.let { putExtra(EXTRA_EXTERNAL_DISPLAY_NAME, it) }
                }
            ExternalViewTarget.MEDIA_PREVIEW ->
                Intent(sourceIntent).setClass(this, MediaPreviewActivity::class.java).apply {
                    setDataAndType(
                        uri,
                        ExternalViewRouteSelector.mediaLaunchMimeType(
                            sourceIntent.type,
                            resolvedMimeType,
                            target
                        )
                    )
                    externalDisplayName?.let { putExtra(EXTRA_EXTERNAL_DISPLAY_NAME, it) }
                }
            ExternalViewTarget.FILE_BROWSER ->
                Intent(sourceIntent).setClass(this, FileListActivity::class.java).apply {
                    externalDisplayName?.let { putExtra(EXTRA_EXTERNAL_DISPLAY_NAME, it) }
                }
            ExternalViewTarget.PACKAGE_INSTALLER -> {
                val packageDisplayName = sourceDisplayName
                    ?.takeIf { AndroidPackageInstallerInput.extension(it) != null }
                    ?: DEFAULT_APK_DISPLAY_NAME
                PackageInstallerActivity.createIntent(this, uri, packageDisplayName)
            }
        }

        startActivity(targetIntent)
    }

    private companion object {
        const val DEFAULT_APK_DISPLAY_NAME = "package.apk"
    }
}

internal enum class ExternalViewTarget {
    SETTINGS_RESTORE,
    TEXT_EDITOR,
    AUDIO_PLAYER,
    MEDIA_PREVIEW,
    FILE_BROWSER,
    PACKAGE_INSTALLER
}

internal object ExternalViewRouteSelector {
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val OCTET_STREAM_MIME_TYPE = "application/octet-stream"
    private const val AUDIO_FALLBACK_MIME_TYPE = "audio/*"
    private const val VIDEO_FALLBACK_MIME_TYPE = "video/*"

    private val textApplicationMimeTypes = setOf(
        "application/ecmascript",
        "application/javascript",
        "application/json",
        "application/typescript",
        "application/x-sh",
        "application/x-shellscript",
        "application/xml",
        "application/yaml"
    )

    fun select(
        intentMimeType: String?,
        resolvedMimeType: String?,
        isBackup: Boolean,
        displayName: String? = null
    ): ExternalViewTarget {
        val declared = intentMimeType.normalizedMimeType()
        val resolved = resolvedMimeType.normalizedMimeType()
        if (isBackup ||
            declared == MimeType.WIZEFILES_BACKUP.value ||
            resolved == MimeType.WIZEFILES_BACKUP.value
        ) {
            return ExternalViewTarget.SETTINGS_RESTORE
        }
        if (declared == APK_MIME_TYPE ||
            resolved == APK_MIME_TYPE ||
            AndroidPackageInstallerInput.extension(displayName.orEmpty()) != null
        ) {
            return ExternalViewTarget.PACKAGE_INSTALLER
        }

        val effective = listOf(declared, resolved)
            .firstOrNull { it != null && it != MimeType.ANY.value && it != MimeType.GENERIC.value }
            ?: resolved
            ?: declared
        val inferredTarget = if (
            effective == null ||
            effective == MimeType.ANY.value ||
            effective == MimeType.GENERIC.value ||
            effective == OCTET_STREAM_MIME_TYPE
        ) {
            InternalOpenPolicy.targetAfterExtraction(
                MimeType(effective ?: MimeType.GENERIC.value),
                displayName
            )
        } else {
            null
        }
        return when {
            effective?.startsWith("audio/") == true ||
                inferredTarget == InternalOpenPolicy.Target.AUDIO_PLAYER ->
                ExternalViewTarget.AUDIO_PLAYER
            effective?.startsWith("video/") == true ||
                inferredTarget == InternalOpenPolicy.Target.VIDEO_PREVIEW ->
                ExternalViewTarget.MEDIA_PREVIEW
            effective != null &&
                (effective.startsWith("text/") || effective in textApplicationMimeTypes) ->
                ExternalViewTarget.TEXT_EDITOR
            else -> ExternalViewTarget.FILE_BROWSER
        }
    }

    fun mediaLaunchMimeType(
        intentMimeType: String?,
        resolvedMimeType: String?,
        target: ExternalViewTarget
    ): String {
        val declared = intentMimeType.normalizedMimeType()
        val resolved = resolvedMimeType.normalizedMimeType()
        return when (target) {
            ExternalViewTarget.AUDIO_PLAYER ->
                listOf(resolved, declared).firstOrNull { it?.startsWith("audio/") == true }
                    ?: AUDIO_FALLBACK_MIME_TYPE
            ExternalViewTarget.MEDIA_PREVIEW ->
                listOf(resolved, declared).firstOrNull { it?.startsWith("video/") == true }
                    ?: VIDEO_FALLBACK_MIME_TYPE
            else -> throw IllegalArgumentException("Target $target is not a media playback target")
        }
    }

    private fun String?.normalizedMimeType(): String? =
        this?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotEmpty)
}
