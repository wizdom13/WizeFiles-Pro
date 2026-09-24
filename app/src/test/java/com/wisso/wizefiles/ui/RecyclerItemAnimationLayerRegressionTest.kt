package com.wisso.wizefiles.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class RecyclerItemAnimationLayerRegressionTest {

    @Test
    fun `recycler item animations do not force transient hardware layers`() {
        val candidates = listOf(
            Paths.get("app/src/main/java/com/wisso/wizefiles/ui/RecyclerItemAnimationHelper.kt"),
            Paths.get("src/main/java/com/wisso/wizefiles/ui/RecyclerItemAnimationHelper.kt")
        )
        val file = candidates.firstOrNull { Files.exists(it) }
            ?: error("Unable to locate RecyclerItemAnimationHelper.kt")
        val source = String(Files.readAllBytes(file), StandardCharsets.UTF_8)

        assertFalse(source.contains(".withLayer()"))
        assertTrue(source.contains("fun applySelectionScale"))
        assertTrue(source.contains("view.animate().cancel()"))
        assertTrue(source.contains("view.setLayerType(View.LAYER_TYPE_NONE, null)"))
    }
}
