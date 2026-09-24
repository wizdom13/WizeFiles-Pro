package com.wisso.wizefiles.feature.filebrowser

import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wisso.wizefiles.settings.SettingsActivity
import com.wisso.wizefiles.ui.FixQueryChangeSearchView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileListSearchBackInstrumentedTest {
    @Test
    fun searchBackHidesEditingBeforeCollapsingTheSession() {
        lateinit var searchView: FixQueryChangeSearchView

        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                searchView = FixQueryChangeSearchView(activity)
                activity.setContentView(searchView)
                searchView.setIconified(false)
                searchView.setQuery("reports", false)
                searchView.findViewById<View>(androidx.appcompat.R.id.search_src_text)
                    .requestFocus()

                assertTrue(searchView.hideImePreservingSearch())
                assertEquals("reports", searchView.query.toString())
            }

            SystemClock.sleep(550)

            scenario.onActivity {
                assertFalse(searchView.hideImePreservingSearch())
                assertEquals("reports", searchView.query.toString())
            }
        }
    }
}
