package com.wisso.wizefiles.feature.fontviewer

import org.junit.Assert.assertEquals
import org.junit.Test

class FontPreviewSizeTest {
    @Test
    fun `fractional restored value is snapped to slider grid`() {
        assertEquals(31f, FontPreviewSize.normalize(31.304348f), 0f)
    }

    @Test
    fun `valid slider value is preserved`() {
        assertEquals(36f, FontPreviewSize.normalize(36f), 0f)
    }

    @Test
    fun `restored value is clamped to slider bounds`() {
        assertEquals(FontPreviewSize.MIN_SP, FontPreviewSize.normalize(-10f), 0f)
        assertEquals(FontPreviewSize.MAX_SP, FontPreviewSize.normalize(200f), 0f)
    }

    @Test
    fun `missing or non finite restored value falls back to default`() {
        assertEquals(FontPreviewSize.DEFAULT_SP, FontPreviewSize.normalize(null), 0f)
        assertEquals(FontPreviewSize.DEFAULT_SP, FontPreviewSize.normalize(Float.NaN), 0f)
        assertEquals(FontPreviewSize.DEFAULT_SP, FontPreviewSize.normalize(Float.POSITIVE_INFINITY), 0f)
    }
}
