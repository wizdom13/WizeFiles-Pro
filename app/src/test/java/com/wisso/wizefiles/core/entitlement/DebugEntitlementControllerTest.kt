package com.wisso.wizefiles.core.entitlement

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DebugEntitlementControllerTest {
    private lateinit var context: Context

    @Before
    fun clearPersistedOverride() {
        context = ApplicationProvider.getApplicationContext()
        DebugEntitlementController.reset(context)
    }

    @After
    fun resetDebugOverride() {
        DebugEntitlementController.reset(context)
    }

    @Test
    fun debugControllerCanExerciseFreeAndProStates() {
        DebugEntitlementController.setProEnabled(true)
        assertTrue(AppEntitlements.repository.state.value.isPro)
        assertTrue(DebugEntitlementController.isProEnabled())

        DebugEntitlementController.setProEnabled(false)
        assertFalse(AppEntitlements.repository.state.value.isPro)
        assertFalse(DebugEntitlementController.isProEnabled())

        DebugEntitlementController.reset()
        assertFalse(AppEntitlements.repository.state.value.isPro)
    }

    @Test
    fun persistedOverrideIsRestoredWhenTheAppInitializes() {
        DebugEntitlementController.setProEnabled(context, true)
        assertTrue(AppEntitlements.repository.state.value.isPro)

        DebugEntitlementController.setProEnabled(false)
        assertFalse(AppEntitlements.repository.state.value.isPro)

        AppEntitlements.initialize(context)

        assertTrue(AppEntitlements.repository.state.value.isPro)
    }
}
