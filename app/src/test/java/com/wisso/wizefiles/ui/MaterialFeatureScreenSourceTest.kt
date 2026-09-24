// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialFeatureScreenSourceTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `local sharing and nearby transfer use the shared Material screen shell`() {
        val helper = source("ui/MaterialFeatureScreen.kt")
        val localShare = source("feature/share/LocalShareActivity.kt")
        val nearby = source("feature/nearby/NearbyTransferActivity.kt")

        assertTrue("MaterialToolbar" in helper)
        assertTrue("MaterialCardView" in helper)
        assertTrue("MaterialButton" in helper)
        assertTrue("WindowInsetsCompat.Type.systemBars()" in helper)
        assertTrue("MaterialFeatureScreen(this,R.string.local_share_title)" in localShare)
        assertTrue("MaterialFeatureScreen(this, R.string.nearby_transfer_title)" in nearby)
        assertFalse("android.widget.Button" in localShare)
        assertFalse("android.widget.Button" in nearby)
        assertFalse("ScrollView(this)" in localShare)
        assertFalse("ScrollView(this)" in nearby)
    }

    private fun source(relative: String) = File(
        root,
        "app/src/main/java/com/wisso/wizefiles/$relative"
    ).readText()
}
