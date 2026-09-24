package com.wisso.wizefiles.feature.filebrowser

import android.os.Bundle
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import org.junit.Assert.assertEquals
import org.junit.Test
import com.wisso.wizefiles.provider.archive.createArchiveRootPath

class TrailDataArchiveNavigationTest {

    @Test
    fun navigateToRealParentFromArchive_clearsArchiveSuffixFromTrail() {
        val archivePath = createTempFile(prefix = "breadcrumb-archive-root", suffix = ".zip")
        try {
            val archiveRoot = archivePath.createArchiveRootPath()
            val parent = archivePath.parent
            val trailData = TrailData.of(archiveRoot)

            val updatedTrailData = trailData.navigateTo(lastState = Bundle(), path = parent)

            assertEquals(parent, updatedTrailData.currentPath)
            assertEquals(parent, updatedTrailData.trail.last())
        } finally {
            archivePath.deleteIfExists()
        }
    }
}
