// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalViewRouteSelectorTest {

    @Test
    fun `wzf name wins over generic mime types`() {
        assertEquals(
            ExternalViewTarget.SETTINGS_RESTORE,
            ExternalViewRouteSelector.select("*/*", null, isBackup = true)
        )
        assertEquals(
            ExternalViewTarget.SETTINGS_RESTORE,
            ExternalViewRouteSelector.select("application/octet-stream", null, isBackup = true)
        )
    }

    @Test
    fun `dedicated backup mime routes to settings`() {
        assertEquals(
            ExternalViewTarget.SETTINGS_RESTORE,
            ExternalViewRouteSelector.select(
                "application/x-wizefiles-backup",
                null,
                isBackup = false
            )
        )
    }

    @Test
    fun `apk mime routes directly to package installer`() {
        assertEquals(
            ExternalViewTarget.PACKAGE_INSTALLER,
            ExternalViewRouteSelector.select(
                "application/vnd.android.package-archive",
                null,
                isBackup = false
            )
        )
        assertEquals(
            ExternalViewTarget.PACKAGE_INSTALLER,
            ExternalViewRouteSelector.select(
                "application/octet-stream",
                "application/vnd.android.package-archive",
                isBackup = false
            )
        )
    }

    @Test
    fun `package filenames route directly to package installer`() {
        listOf("base.apk", "bundle.apks", "mirror.APKM", "game.xapk").forEach { displayName ->
            assertEquals(
                displayName,
                ExternalViewTarget.PACKAGE_INSTALLER,
                ExternalViewRouteSelector.select(
                    "application/zip",
                    null,
                    isBackup = false,
                    displayName = displayName
                )
            )
        }
    }

    @Test
    fun `misleading package filenames remain in browser`() {
        listOf("bundle.aab", "game.xapk.zip", "apk").forEach { displayName ->
            assertEquals(
                displayName,
                ExternalViewTarget.FILE_BROWSER,
                ExternalViewRouteSelector.select(
                    "application/zip",
                    null,
                    isBackup = false,
                    displayName = displayName
                )
            )
        }
    }

    @Test
    fun `backup routing takes precedence over package filename`() {
        assertEquals(
            ExternalViewTarget.SETTINGS_RESTORE,
            ExternalViewRouteSelector.select(
                "application/octet-stream",
                null,
                isBackup = true,
                displayName = "misleading.apk"
            )
        )
    }

    @Test
    fun `audio mime routes to audio player`() {
        assertEquals(
            ExternalViewTarget.AUDIO_PLAYER,
            ExternalViewRouteSelector.select("audio/mpeg", null, isBackup = false)
        )
        assertEquals(
            ExternalViewTarget.AUDIO_PLAYER,
            ExternalViewRouteSelector.select(
                "application/octet-stream",
                "audio/flac",
                isBackup = false
            )
        )
    }

    @Test
    fun `video mime routes to media preview`() {
        assertEquals(
            ExternalViewTarget.MEDIA_PREVIEW,
            ExternalViewRouteSelector.select("video/mp4", null, isBackup = false)
        )
        assertEquals(
            ExternalViewTarget.MEDIA_PREVIEW,
            ExternalViewRouteSelector.select(
                "application/octet-stream",
                "video/webm",
                isBackup = false
            )
        )
    }

    @Test
    fun `generic media mime uses provider display filename`() {
        assertEquals(
            ExternalViewTarget.AUDIO_PLAYER,
            ExternalViewRouteSelector.select(
                "application/octet-stream",
                null,
                isBackup = false,
                displayName = "song.mp3"
            )
        )
        assertEquals(
            ExternalViewTarget.MEDIA_PREVIEW,
            ExternalViewRouteSelector.select(
                "*/*",
                null,
                isBackup = false,
                displayName = "movie.mp4"
            )
        )
    }

    @Test
    fun `media launch mime prefers resolved concrete type and provides target fallback`() {
        assertEquals(
            "audio/flac",
            ExternalViewRouteSelector.mediaLaunchMimeType(
                "application/octet-stream",
                "audio/flac; charset=binary",
                ExternalViewTarget.AUDIO_PLAYER
            )
        )
        assertEquals(
            "video/mp4",
            ExternalViewRouteSelector.mediaLaunchMimeType(
                "video/mp4",
                null,
                ExternalViewTarget.MEDIA_PREVIEW
            )
        )
        assertEquals(
            "audio/*",
            ExternalViewRouteSelector.mediaLaunchMimeType(
                null,
                null,
                ExternalViewTarget.AUDIO_PLAYER
            )
        )
        assertEquals(
            "video/*",
            ExternalViewRouteSelector.mediaLaunchMimeType(
                "*/*",
                null,
                ExternalViewTarget.MEDIA_PREVIEW
            )
        )
    }

    @Test
    fun `text mime routes to editor`() {
        assertEquals(
            ExternalViewTarget.TEXT_EDITOR,
            ExternalViewRouteSelector.select("text/plain", null, isBackup = false)
        )
        assertEquals(
            ExternalViewTarget.TEXT_EDITOR,
            ExternalViewRouteSelector.select("application/json", null, isBackup = false)
        )
    }

    @Test
    fun `resolved text mime overrides generic declaration`() {
        assertEquals(
            ExternalViewTarget.TEXT_EDITOR,
            ExternalViewRouteSelector.select(
                "application/octet-stream",
                "text/plain",
                isBackup = false
            )
        )
    }

    @Test
    fun `archives and unknown files route to browser`() {
        assertEquals(
            ExternalViewTarget.FILE_BROWSER,
            ExternalViewRouteSelector.select("application/zip", null, isBackup = false)
        )
        assertEquals(
            ExternalViewTarget.FILE_BROWSER,
            ExternalViewRouteSelector.select("*/*", null, isBackup = false)
        )
    }
}
