package com.wisso.wizefiles.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FileIconShapeSourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `shape ids are stable and squircle is the default`() {
        assertEquals(
            linkedSetOf(
                "squircle",
                "rounded_square",
                "flower",
                "square",
                "teardrop",
                "pebble",
                "vessel",
                "pentagon",
                "hexagon_1",
                "hexagon_2",
                "heptagon",
                "octagon"
            ),
            FileIconShape.stableIds
        )
        assertSame(FileIconShape.SQUIRCLE, FileIconShape.DEFAULT)
        assertSame(FileIconShape.DEFAULT, FileIconShape.fromStableId("unknown"))
    }

    @Test
    fun `appearance setting uses preview rows and stable values`() {
        val settings = sourceFile("app/src/main/res/xml/settings.xml")
        val values = sourceFile("app/src/main/res/values/file_icon_shape_preferences.xml")
        val row = sourceFile("app/src/main/res/layout/item_icon_shape_choice.xml")

        assertTrue("IconShapePreference" in settings)
        assertTrue("@string/pref_default_value_file_icon_shape" in settings)
        assertTrue(
            "pref_default_value_file_icon_shape\" translatable=\"false\">squircle</string>" in values
        )
        assertTrue("<item>rounded_square</item>" in values)
        assertTrue("FileIconShapeView" in row)
        assertTrue("@+id/shapePreview" in row)
        assertTrue("app:fileIconShowPrimaryOutline=\"true\"" in row)

        val shapeView = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/ui/FileIconShapeView.kt"
        )
        assertTrue("colorPrimary" in shapeView)
        assertTrue("if (showPrimaryOutline)" in shapeView)
        assertTrue("PRIMARY_OUTLINE_STROKE_WIDTH_DP = 2f" in shapeView)

        val preference = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/settings/IconShapePreference.kt"
        )
        assertTrue("override fun onSetInitialValue" in preference)
        assertTrue("FileIconShape.fromStableId(persistedStableId).stableId" in preference)
    }

    @Test
    fun `file icons and media thumbnails share the adaptive mask`() {
        val shapeView = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/ui/FileIconShapeView.kt"
        )
        assertTrue("colorPrimaryContainer" in shapeView)
        assertTrue("colorOnPrimaryContainer" in shapeView)
        assertTrue("Settings.FILE_ICON_SHAPE.observeForever" in shapeView)
        assertTrue("canvas.clipPath(maskPath)" in shapeView)
        assertTrue("canvas.drawPath(maskPath, outlinePaint)" in shapeView)

        val adapter = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListAdapter.kt"
        )
        assertTrue("onSuccess = { _, _ ->" in adapter)
        assertTrue("onError = { _, _ ->" in adapter)
        assertTrue("if (holder.boundPath == path)" in adapter)
        assertTrue("showAppIcon()" in adapter)
        assertTrue("showBuiltInIcon()" in adapter)
        assertTrue("showPlainImage()" !in adapter)

        listOf(
            "app/src/main/res/layout/item_file_list.xml",
            "app/src/main/res/layout/item_file_grid.xml",
            "app/src/main/res/layout/item_vault_entry_list.xml",
            "app/src/main/res/layout/dialog_file_job_conflict.xml",
            "app/src/main/res/layout/item_storage_cleaner_recommendation.xml"
        ).forEach { path ->
            val source = sourceFile(path)
            assertTrue("FileIconShapeView missing from $path", "FileIconShapeView" in source)
            assertTrue(
                "Primary preview outline must not be enabled in $path",
                "fileIconShowPrimaryOutline" !in source
            )
            if (path.endsWith("item_file_list.xml") ||
                path.endsWith("item_vault_entry_list.xml")
            ) {
                assertTrue("Details must use logical start alignment", "android:gravity=\"start\"" in source)
                assertTrue("Dates must use logical end alignment", "android:gravity=\"end\"" in source)
                assertTrue("Date column missing from $path", "@+id/dateText" in source)
            }
        }

        assertTrue(
            "app:fileIconVisualStyle=\"thumbnail\"" in
                sourceFile("app/src/main/res/layout/item_file_grid.xml")
        )
        assertTrue(
            "app:fileIconShowPrimaryOutline=\"true\"" !in
                sourceFile("app/src/main/res/layout/item_file_grid.xml")
        )
    }

    @Test
    fun `launcher artwork uses the circular app icon path`() {
        val shapeView = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/ui/FileIconShapeView.kt"
        )
        assertTrue("APP_ICON" in shapeView)
        assertTrue("fun showAppIcon()" in shapeView)
        assertTrue("canvas.clipPath(appIconMaskPath)" in shapeView)
        assertTrue("addCircle(" in shapeView)

        val adapter = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListAdapter.kt"
        )
        assertTrue("file.mimeType.isApk" in adapter)
        assertTrue("showAppIcon()" in adapter)

        val conflictDialog = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filejobs/" +
                "FileOperationConflictDialogFragment.kt"
        )
        assertTrue("file.mimeType.isApk" in conflictDialog)
        assertTrue("showAppIcon()" in conflictDialog)

        val storageCleaner = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/storagecleaner/" +
                "StorageCleanerActivity.kt"
        )
        val storageCleanerAdapter = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/storagecleaner/" +
                "StorageCleanerRecommendationsAdapter.kt"
        )
        val storageCleanerPresentation = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/storagecleaner/" +
                "StorageCleanerPresentation.kt"
        )
        assertTrue("Pair<AppPath, FileMetadata>" in storageCleanerPresentation)
        assertTrue(
            "previewPath.toAppPath() to FileMetadata(" in storageCleanerPresentation
        )
        assertTrue("preview.showAppIcon()" in storageCleanerAdapter)
        assertTrue("Pair<Path, BasicFileAttributes>" !in storageCleaner)
        assertTrue("Pair<Path, BasicFileAttributes>" !in storageCleanerPresentation)
    }

    private fun sourceFile(path: String): String = File(root, path).readText()
}
