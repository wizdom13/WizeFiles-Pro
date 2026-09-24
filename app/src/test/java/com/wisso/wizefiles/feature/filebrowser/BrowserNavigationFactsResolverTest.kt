// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import com.wisso.wizefiles.provider.archive.createArchiveRootPath
import com.wisso.wizefiles.provider.sftp.SftpFileSystemProvider
import java.net.URI
import java.nio.file.Paths
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowserNavigationFactsResolverTest {
    @Test fun `local paths resolve containment by Path semantics rather than string prefix`() {
        val root = Paths.get("/storage/root")
        val child = BrowserNavigationFactsResolver.resolve(root.resolve("child"), root)
        val textualPrefixOnly = BrowserNavigationFactsResolver.resolve(Paths.get("/storage/root2"), root)

        assertTrue(child.hasParent)
        assertTrue(child.isWithinConfiguredRoot)
        assertFalse(child.isConfiguredRoot)
        assertFalse(textualPrefixOnly.isWithinConfiguredRoot)
    }

    @Test fun `archive path resolves a container-boundary transition`() {
        val archive = createTempFile(prefix = "browser-navigation", suffix = ".zip")
        try {
            val facts = BrowserNavigationFactsResolver.resolve(
                archive.createArchiveRootPath(),
                Paths.get("/storage/emulated/0")
            )
            assertTrue(facts.crossesContainerBoundary)
            assertTrue(BrowserNavigationPolicy.canNavigateUp(facts))
        } finally {
            archive.deleteIfExists()
        }
    }

    @Test fun `remote SFTP root maps to neutral root facts without a connection`() {
        val root = SftpFileSystemProvider.getPath(URI("sftp://demo@example.test/"))
        val facts = BrowserNavigationFactsResolver.resolve(root, root)

        assertTrue(facts.configuredRootAvailable)
        assertTrue(facts.isConfiguredRoot)
        assertTrue(facts.isWithinConfiguredRoot)
        assertFalse(BrowserNavigationPolicy.canNavigateUp(facts))
    }
}
