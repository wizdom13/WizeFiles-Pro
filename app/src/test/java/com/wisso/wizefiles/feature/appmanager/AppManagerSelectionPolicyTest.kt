package com.wisso.wizefiles.feature.appmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppManagerSelectionPolicyTest {
    @Test
    fun `uninstall requires only user apps`() {
        assertFalse(canUninstallSelectedApps(emptyList()))
        assertTrue(canUninstallSelectedApps(listOf(app("user"))))
        assertFalse(canUninstallSelectedApps(listOf(app("system", isSystem = true))))
        assertFalse(
            canUninstallSelectedApps(
                listOf(app("user"), app("system", isSystem = true))
            )
        )
    }

    @Test
    fun `enable disable is a single app system mediated action`() {
        assertEquals(
            AppEnabledAction.DISABLE,
            selectedAppEnabledAction(listOf(app("system", isSystem = true)))
        )
        assertEquals(
            AppEnabledAction.ENABLE,
            selectedAppEnabledAction(
                listOf(app("disabled", isSystem = true, isEnabled = false))
            )
        )
        assertEquals(
            AppEnabledAction.ENABLE,
            selectedAppEnabledAction(listOf(app("disabled-user", isEnabled = false)))
        )
        assertNull(selectedAppEnabledAction(listOf(app("user"))))
        assertNull(selectedAppEnabledAction(listOf(app("one"), app("two"))))
    }

    private fun app(
        packageName: String,
        isSystem: Boolean = false,
        isEnabled: Boolean = true
    ) = InstalledApp(
        packageName = packageName,
        label = packageName,
        versionName = "1",
        versionCode = 1,
        isSystem = isSystem,
        isEnabled = isEnabled,
        isSplit = false,
        totalApkBytes = 1,
        firstInstallTimeMillis = 1,
        lastUpdateTimeMillis = 1,
        sourceApkPaths = emptyList(),
        hasLaunchIntent = isEnabled
    )
}
