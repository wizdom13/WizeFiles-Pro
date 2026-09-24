package com.wisso.wizefiles.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugProSettingsSourceTest {
    @Test
    fun `App Settings installs the debug entitlement control`() {
        val fragment =
            sourceFile("src/main/java/com/wisso/wizefiles/feature/settings/SettingsPreferenceFragment.kt")
        val debugUi =
            sourceFile("src/debug/java/com/wisso/wizefiles/feature/settings/DebugEntitlementSettings.kt")

        assertTrue(
            fragment.contains(
                "DebugEntitlementSettings.install(requireContext(), preferenceScreen)"
            )
        )
        assertTrue(debugUi.contains("title = \"Simulate Pro access\""))
        assertTrue(debugUi.contains("isPersistent = false"))
        assertTrue(
            debugUi.contains(
                "DebugEntitlementController.setProEnabled(context, value as Boolean)"
            )
        )
    }

    @Test
    fun `the debug override is private persistent and restored at startup`() {
        val source =
            sourceFile("src/debug/java/com/wisso/wizefiles/core/entitlement/AppEntitlements.kt")

        assertTrue(source.contains("context.applicationContext.getSharedPreferences"))
        assertTrue(source.contains("putBoolean(KEY_SIMULATE_PRO, isPro)"))
        assertTrue(source.contains("getBoolean(KEY_SIMULATE_PRO, false)"))
        assertTrue(source.contains("source.setProEnabled"))
    }

    @Test
    fun `Beta and Release do not expose the debug switch`() {
        listOf("beta", "release").forEach { variant ->
            val source =
                sourceFile(
                    "src/$variant/java/com/wisso/wizefiles/feature/settings/DebugEntitlementSettings.kt"
                )
            assertFalse(source.contains("Simulate Pro access"))
            assertFalse(source.contains("DebugEntitlementController"))
        }
    }

    private fun sourceFile(path: String): String {
        val direct = File(path)
        if (direct.exists()) return direct.readText()
        val fromRepoRoot = File("app", path)
        if (fromRepoRoot.exists()) return fromRepoRoot.readText()
        throw java.io.FileNotFoundException(path)
    }
}
