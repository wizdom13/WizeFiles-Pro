// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SigningTextInputLayoutLaunchTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `APK sign screen launches`() {
        assertLaunches(ApkSignVerifyActivity::class.java, "apk_signing.mode", "SIGN")
    }

    @Test
    fun `APK verify screen launches`() {
        assertLaunches(ApkSignVerifyActivity::class.java, "apk_signing.mode", "VERIFY")
    }

    @Test
    fun `AAB sign screen launches`() {
        assertLaunches(AabSignVerifyActivity::class.java, "aab_signing.mode", "SIGN")
    }

    @Test
    fun `APKS sign screen launches`() {
        assertLaunches(ApksSignVerifyActivity::class.java, "apks_signing.mode", "SIGN")
    }

    @Test
    fun `XAPK sign screen launches`() {
        assertLaunches(XapkSignVerifyActivity::class.java, "xapk_signing.mode", "SIGN")
    }

    private fun <T : AppCompatActivity> assertLaunches(
        activityClass: Class<T>,
        modeExtra: String,
        mode: String
    ) {
        val controller = Robolectric.buildActivity(
            activityClass,
            Intent(context, activityClass).putExtra(modeExtra, mode)
        ).setup()
        assertFalse(controller.get().isFinishing)
        controller.pause().stop().destroy()
    }
}
