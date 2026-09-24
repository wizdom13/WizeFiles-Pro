package com.wisso.wizefiles.navigation

import android.content.Context
import com.wisso.wizefiles.provider.archive.createArchiveRootPath
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationRootLookupTest {

    @Test
    fun `findNavigationRoot matches by normalized path string when Path equality differs`() {
        val root = FakeNavigationRoot(Paths.get("/storage/emulated/0"))
        val map = mapOf(root.path to root)

        val aliasPath = AliasPath("/storage/emulated/0")

        assertEquals(root, findNavigationRoot(aliasPath, map))
    }

    @Test
    fun `findNavigationRoot returns null when no matching root exists`() {
        val root = FakeNavigationRoot(Paths.get("/storage/emulated/0"))
        val map = mapOf(root.path to root)

        assertNull(findNavigationRoot(Paths.get("/data/local/tmp"), map))
    }

    @Test
    fun `archive virtual root is not mistaken for device root`() {
        val archiveFile = createTempFile(prefix = "navigation-root", suffix = ".zip")
        val archiveRoot = archiveFile.createArchiveRootPath()
        try {
            val deviceRoot = FakeNavigationRoot(Paths.get("/"))

            assertNull(findNavigationRoot(archiveRoot, mapOf(deviceRoot.path to deviceRoot)))
        } finally {
            archiveRoot.fileSystem.close()
            archiveFile.deleteIfExists()
        }
    }

    private data class FakeNavigationRoot(override val path: Path) : NavigationRoot {
        override val iconRes: Int = 0

        override fun getName(context: Context): String = "fake"
    }

    private class AliasPath(private val displayPath: String) : Path by Paths.get("/") {
        override fun toString(): String = displayPath
    }
}
