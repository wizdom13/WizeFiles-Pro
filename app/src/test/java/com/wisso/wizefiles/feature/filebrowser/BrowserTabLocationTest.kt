// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserTabLocationTest {
    @Test
    fun devicePathSurvivesEvenWhenVolumeIsTemporarilyUnmounted() {
        val path = Paths.get("/storage/1234-ABCD/My Photos")
        val saved = BrowserTabLocation.capture(path, true, emptyList())!!

        assertEquals(path, saved.resolve(emptyList()))
    }

    @Test
    fun configuredLocationStoresRelativePathAndUsesCurrentStorageConfiguration() {
        val roots = listOf(1L to Paths.get("/connection"), 2L to Paths.get("/connection/shared"))
        val saved = BrowserTabLocation.capture(Paths.get("/connection/shared/reports/2026"), false, roots)!!

        assertEquals(BrowserTabLocation("reports/2026", 2L), saved)
        assertEquals(Paths.get("/new-connection/reports/2026"), saved.resolve(
            listOf(2L to Paths.get("/new-connection"))
        ))
        assertNull(saved.resolve(emptyList()))
    }

    @Test
    fun unconfiguredProviderLocationsAreNotPersisted() {
        assertNull(BrowserTabLocation.capture(Paths.get("/temporary/grant"), false, emptyList()))
    }

    @Test
    fun malformedPathsAndReferencesOutsideTheirStorageAreRejected() {
        val roots = listOf(1L to Paths.get("/connection/shared"))
        assertNull(BrowserTabLocation("../secret", 1L).resolve(roots))
        assertNull(BrowserTabLocation("relative").resolve(roots))
        assertNull(BrowserTabLocation("\u0000").resolve(roots))
    }
}
