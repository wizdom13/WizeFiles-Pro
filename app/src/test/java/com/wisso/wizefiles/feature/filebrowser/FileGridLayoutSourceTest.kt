package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileGridLayoutSourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `grid item uses exactly one fallback icon and a full width label`() {
        val layout = sourceFile("app/src/main/res/layout/item_file_grid.xml")
        val adapter = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListAdapter.kt"
        )

        assertEquals(1, Regex("@\\+id/thumbnailIconImage").findAll(layout).count())
        assertFalse("@+id/iconImage" in layout)
        assertTrue("@+id/iconLayout" in layout.substringBefore("thumbnailIconImage"))
        val fallbackIcon = layout.substringAfter("@+id/thumbnailIconImage")
            .substringBefore("/>")
        assertTrue("android:layout_width=\"match_parent\"" in fallbackIcon)
        assertTrue("android:layout_height=\"match_parent\"" in fallbackIcon)
        assertTrue("android:layout_width=\"match_parent\"" in layout.substringAfter("@+id/nameText"))
        assertTrue("binding.thumbnailIconImage,\n            binding.directoryThumbnailImage" in adapter)
        assertTrue("holder.itemLayout.apply {" in adapter)
        assertTrue("setOnLongClickListener {" in adapter)
        assertFalse("holder.iconLayout.setOnClickListener" in adapter)
        assertTrue("holder.thumbnailOutlineView?.isVisible = shouldLoadThumbnail" in adapter)
        val fragment = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        )
        val renderCoordinator = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListRenderCoordinator.kt"
        )
        assertTrue("GridLayoutPolicy.spanCount(" in fragment)
        assertTrue("addOnLayoutChangeListener" in fragment)
        assertTrue("val spanViewType = viewType ?: appliedViewType" in renderCoordinator)
        assertTrue(
            "pendingSpanCountUpdate = pendingSpanCountUpdate && spanViewType == null" in
                renderCoordinator
        )
        assertFalse("calculateSpanCount(viewType ?: viewModel.viewType)" in renderCoordinator)
        assertTrue("recyclerView.recycledViewPool.clear()" in renderCoordinator)
        assertTrue("adapter.recreateGridViewHoldersForSpanChange()" in renderCoordinator)
        assertTrue("recyclerView.requestLayout()" in renderCoordinator)
        assertTrue("fun recreateGridViewHoldersForSpanChange()" in adapter)
        assertTrue("gridLayoutGeneration += 1" in adapter)
        assertTrue("notifyDataSetChanged()" in adapter)
        assertTrue("viewTypeCode % FileViewType.entries.size" in adapter)

        val iconShapeView = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/ui/FileIconShapeView.kt"
        )
        assertTrue(
            "drawMaskedContent(canvas, BUILT_IN_ICON_CONTENT_SCALE)" in iconShapeView
        )
        assertTrue("BUILT_IN_ICON_CONTENT_SCALE = 0.6f" in iconShapeView)
        assertFalse("updateContentPadding" in iconShapeView)
        assertFalse("setPadding(" in iconShapeView)

        assertTrue("private fun resetLoadedImages(holder: ViewHolder)" in adapter)
        assertTrue("holder.boundPath = null" in adapter)
        assertTrue("holder.boundPath = path" in adapter)
        assertTrue("if (holder.boundPath == path)" in adapter)
        assertTrue("val supportsThumbnail = !isDirectory && file.supportsThumbnail" in adapter)

        val extensions = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileItemExtensions.kt"
        )
        val thumbnailSupport = extensions.substringAfter("val FileItem.supportsThumbnail")
        val directoryGuard = thumbnailSupport.indexOf("if (attributes.isDirectory)")
        val previewableTypeGuard = thumbnailSupport.indexOf("if (!isPreviewableType)")
        val documentThumbnailShortcut = thumbnailSupport.indexOf(
            "if (legacyPath.isDocumentPath && attributes.documentSupportsThumbnail)"
        )
        assertTrue(directoryGuard >= 0)
        assertTrue(previewableTypeGuard > directoryGuard)
        assertTrue(documentThumbnailShortcut > previewableTypeGuard)
        assertTrue("val isPreviewableType =" in thumbnailSupport)

        val aspectRatioLayout = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/ui/AspectRatioFrameLayout.kt"
        )
        assertTrue("AspectRatioFrameLayout_aspectRatioMaxWidth" in aspectRatioLayout)
        assertTrue("constrainWidth(" in aspectRatioLayout)

        val vaultLayout = sourceFile("app/src/main/res/layout/item_vault_entry_grid.xml")
        listOf(layout, vaultLayout).forEach { gridLayout ->
            assertTrue("app:aspectRatio=\"1.0\"" in gridLayout)
            assertTrue("app:aspectRatioMaxWidth=\"@dimen/file_grid_icon_max_size\"" in gridLayout)
            assertTrue("android:layout_gravity=\"center_horizontal\"" in gridLayout)
            assertFalse("app:aspectRatio=\"1.78\"" in gridLayout)
            val iconLayout = gridLayout.substringAfter("@+id/iconLayout").substringBefore(">")
            assertTrue("@dimen/screen_edge_margin_minus_8dp" in iconLayout)
            val nameText = gridLayout.substringAfter("@+id/nameText").substringBefore("/>")
            assertTrue("@dimen/screen_edge_margin_minus_8dp" in nameText)
        }
    }

    private fun sourceFile(path: String): String = File(root, path).readText()
}
