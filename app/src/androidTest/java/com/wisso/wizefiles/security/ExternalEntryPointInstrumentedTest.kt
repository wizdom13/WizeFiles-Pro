// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.security

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wisso.wizefiles.feature.filebrowser.ExternalViewRouterActivity
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalEntryPointInstrumentedTest {
    @Test
    fun browserSurvivesFrameworkStateRecreation() {
        ActivityScenario.launch(FileListActivity::class.java).use { scenario ->
            scenario.recreate()
            assertTrue(scenario.state == Lifecycle.State.RESUMED || scenario.state == Lifecycle.State.STARTED)
        }
    }

    @Test
    fun malformedExternalViewUriFailsWithoutEscapingRouter() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("file:///../../data/system/users.xml"))
            .setType("application/zip")
            .setClass(context, ExternalViewRouterActivity::class.java)
        ActivityScenario.launch<ExternalViewRouterActivity>(intent).use { scenario ->
            // Launch/dispatch itself is the assertion: an uncaught routing failure fails instrumentation.
            scenario.moveToState(Lifecycle.State.DESTROYED)
        }
    }
}
