package com.wisso.wizefiles.storagecleaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageTreemapLayoutTest {
    @Test
    fun `layout fills the map in proportion to positive weights`() {
        val layout = buildTreemapLayout(
            listOf(
                TreemapWeight("Videos", 60L),
                TreemapWeight("Apps", 30L),
                TreemapWeight("Other", 10L),
                TreemapWeight("Empty", 0L)
            )
        )

        assertEquals(3, layout.size)
        assertEquals(1f, layout.sumOf { it.area.toDouble() }.toFloat(), 0.0001f)
        assertEquals(0.6f, layout.single { it.value == "Videos" }.area, 0.0001f)
        assertEquals(0.3f, layout.single { it.value == "Apps" }.area, 0.0001f)
        assertEquals(0.1f, layout.single { it.value == "Other" }.area, 0.0001f)
        assertTrue(layout.all { it.left >= 0f && it.top >= 0f && it.right <= 1f && it.bottom <= 1f })
    }

    @Test
    fun `empty and non-positive input produces no tiles`() {
        assertTrue(buildTreemapLayout<String>(emptyList()).isEmpty())
        assertTrue(buildTreemapLayout(listOf(TreemapWeight("Empty", 0L))).isEmpty())
    }
}
