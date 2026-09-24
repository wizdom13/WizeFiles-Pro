package com.wisso.wizefiles.feature.filebrowser

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.wisso.wizefiles.R
import com.wisso.wizefiles.ui.FixQueryChangeSearchView
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class FileListSearchBackInteractionRobolectricTest {
    @Test
    fun `search Back contract hides editing before allowing the session to collapse`() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Theme_WizeFiles)
        controller.setup()
        val activity = controller.get()
        val searchView = FixQueryChangeSearchView(activity)
        activity.setContentView(searchView)
        searchView.setIconified(false)
        searchView.setQuery("reports", false)
        searchView.findViewById<View>(androidx.appcompat.R.id.search_src_text).requestFocus()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(searchView.hideImePreservingSearch())
        assertEquals("reports", searchView.query.toString())

        ShadowSystemClock.advanceBy(Duration.ofMillis(501))

        assertFalse(searchView.hideImePreservingSearch())
        assertEquals("reports", searchView.query.toString())
        controller.pause().stop().destroy()
    }
}
