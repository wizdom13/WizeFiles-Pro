package com.wisso.wizefiles.feature.filebrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserListPresentationPolicyTest {
    @Test
    fun `initial load uses blocking progress`() {
        val result = BrowserListPresentationPolicy.decide(BrowserLoadPhase.LOADING, null, false)
        assertEquals(BrowserSubtitle.Loading, result.subtitle)
        assertTrue(result.showBlockingProgress)
        assertFalse(result.showRefresh)
    }

    @Test
    fun `refresh and search loading preserve content`() {
        val refresh = BrowserListPresentationPolicy.decide(
            BrowserLoadPhase.LOADING,
            FileListSubtitleCounts(2, 3),
            false
        )
        assertEquals(BrowserSubtitle.Loading, refresh.subtitle)
        assertTrue(refresh.showRefresh)
        assertFalse(refresh.showBlockingProgress)

        val search = BrowserListPresentationPolicy.decide(BrowserLoadPhase.LOADING, null, true)
        assertEquals(BrowserSubtitle.Counts(0, 0), search.subtitle)
        assertTrue(search.showRefresh)
    }

    @Test
    fun `failure keeps stale items but blocks an empty list`() {
        val stale = BrowserListPresentationPolicy.decide(
            BrowserLoadPhase.FAILURE,
            FileListSubtitleCounts(0, 1),
            false
        )
        assertEquals(BrowserSubtitle.Error, stale.subtitle)
        assertFalse(stale.showError)

        val empty = BrowserListPresentationPolicy.decide(BrowserLoadPhase.FAILURE, null, false)
        assertTrue(empty.showError)
        assertFalse(empty.showEmpty)
    }

    @Test
    fun `successful empty list exposes empty presentation`() {
        val result = BrowserListPresentationPolicy.decide(
            BrowserLoadPhase.SUCCESS,
            FileListSubtitleCounts(0, 0),
            false
        )
        assertEquals(BrowserSubtitle.Counts(0, 0), result.subtitle)
        assertTrue(result.showEmpty)
    }
}
