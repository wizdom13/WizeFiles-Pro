package com.wisso.wizefiles.feature.appmanager

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppManagerQueryTest {
    private val apps = listOf(
        app("user.alpha", "Alpha", size = 30, installed = 300, updated = 600),
        app("system.beta", "Beta", system = true, size = 10, installed = 100, updated = 500),
        app("user.gamma", "Gamma", enabled = false, size = 20, installed = 200, updated = 400)
    )

    @Test
    fun `user filter is the default inventory boundary`() {
        val result = query(filter = AppManagerFilter.USER)

        assertEquals(listOf("user.alpha", "user.gamma"), result.map(InstalledApp::packageName))
    }

    @Test
    fun `disabled filter includes disabled packages regardless of package class`() {
        val result = query(filter = AppManagerFilter.DISABLED)

        assertEquals(listOf("user.gamma"), result.map(InstalledApp::packageName))
    }

    @Test
    fun `search matches labels package names and versions`() {
        assertEquals(listOf("user.alpha"), query(query = "alp").map(InstalledApp::packageName))
        assertEquals(listOf("system.beta"), query(query = "system").map(InstalledApp::packageName))
        assertEquals(listOf("user.gamma"), query(query = "3.0").map(InstalledApp::packageName))
    }

    @Test
    fun `size and date sorts honor direction`() {
        assertEquals(
            listOf("system.beta", "user.gamma", "user.alpha"),
            query(sort = AppManagerSort.APK_SIZE).map(InstalledApp::packageName)
        )
        assertEquals(
            listOf("user.alpha", "user.gamma", "system.beta"),
            query(
                sort = AppManagerSort.INSTALLED_DATE,
                order = AppManagerSortOrder.DESCENDING
            ).map(InstalledApp::packageName)
        )
    }

    private fun query(
        query: String = "",
        filter: AppManagerFilter = AppManagerFilter.ALL,
        sort: AppManagerSort = AppManagerSort.NAME,
        order: AppManagerSortOrder = AppManagerSortOrder.ASCENDING
    ): List<InstalledApp> = queryInstalledApps(
        apps = apps,
        query = query,
        filter = filter,
        sort = sort,
        order = order,
        locale = Locale.US
    )

    private fun app(
        packageName: String,
        label: String,
        system: Boolean = false,
        enabled: Boolean = true,
        size: Long,
        installed: Long,
        updated: Long
    ) = InstalledApp(
        packageName = packageName,
        label = label,
        versionName = when (packageName) {
            "user.alpha" -> "1.0"
            "system.beta" -> "2.0"
            else -> "3.0"
        },
        versionCode = 1,
        isSystem = system,
        isEnabled = enabled,
        isSplit = false,
        totalApkBytes = size,
        firstInstallTimeMillis = installed,
        lastUpdateTimeMillis = updated,
        sourceApkPaths = listOf("/data/app/$packageName/base.apk"),
        hasLaunchIntent = true
    )
}
