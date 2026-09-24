package com.wisso.wizefiles.feature.appmanager

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppManagerSourceContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `drawer and manifest expose the dedicated App Manager`() {
        val navigation = source(
            "app/src/main/java/com/wisso/wizefiles/feature/navigation/NavigationItems.kt"
        )
        val manifest = source("app/src/main/AndroidManifest.xml")

        assertTrue("navigation_app_manager" in navigation)
        assertTrue("AppManagerActivity::class.createIntent()" in navigation)
        assertTrue("feature.appmanager.AppManagerActivity" in manifest)
        assertTrue("android.permission.QUERY_ALL_PACKAGES" in manifest)
        assertTrue("android.permission.REQUEST_DELETE_PACKAGES" in manifest)
    }

    @Test
    fun `selection panel contains the approved five actions`() {
        val layout = source("app/src/main/res/layout/activity_app_manager.xml")
        listOf(
            "uninstallAction",
            "enableDisableAction",
            "shareAction",
            "infoAction",
            "openAction"
        ).forEach { assertTrue(it in layout) }
        assertFalse("selectAllAction" in layout)
    }

    @Test
    fun `selected card uses one explicit check indicator`() {
        val layout = source("app/src/main/res/layout/item_installed_app.xml")

        assertTrue("appSelectedImage" in layout)
        assertTrue("app:checkedIcon=\"@null\"" in layout)
    }

    @Test
    fun `split badge shares the version row and uses the primary container`() {
        val layout = source("app/src/main/res/layout/item_installed_app.xml")
        val styles = source("app/src/main/res/values/app_manager_styles.xml")
        val background = source("app/src/main/res/drawable/bg_app_manager_split_badge.xml")
        val versionRowStart = layout.indexOf("appVersionSizeRow")
        val versionRowEnd = layout.indexOf("</LinearLayout>", versionRowStart)
        val splitBadge = layout.indexOf("appSplitBadge")

        assertTrue(versionRowStart >= 0)
        assertTrue(versionRowEnd > versionRowStart)
        assertTrue(splitBadge in versionRowStart until versionRowEnd)
        assertTrue("Widget.WizeFiles.AppManager.SplitBadge" in layout)
        assertTrue("colorOnPrimaryContainer" in styles)
        assertTrue("colorPrimaryContainer" in background)
    }

    @Test
    fun `package actions remain user confirmed and single profile`() {
        val activity = source(
            "app/src/main/java/com/wisso/wizefiles/feature/appmanager/AppManagerActivity.kt"
        )
        val repository = source(
            "app/src/main/java/com/wisso/wizefiles/feature/appmanager/InstalledAppRepository.kt"
        )
        val featureSources = File(
            root,
            "app/src/main/java/com/wisso/wizefiles/feature/appmanager"
        ).walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }

        assertTrue("Intent.ACTION_DELETE" in activity)
        assertTrue("canUninstallSelectedApps(selectedApps)" in activity)
        assertTrue("selectedAppEnabledAction(selectedApps)" in activity)
        assertTrue("appSettingsLauncher.launch(intent)" in activity)
        assertTrue("ActivityResultContracts.StartActivityForResult()" in activity)
        assertTrue("ArrayDeque<String>()" in activity)
        assertTrue("getInstalledApplications" in repository)
        assertFalse("UserManager" in repository)
        assertFalse("LauncherApps" in repository)
        assertFalse("LibSu" in featureSources)
        assertFalse("Shizuku" in featureSources)
        assertFalse("setApplicationEnabledSetting" in featureSources)
    }

    @Test
    fun `backup uses complete split archives secure sharing and Transfer Center`() {
        val activity = source(
            "app/src/main/java/com/wisso/wizefiles/feature/appmanager/AppManagerActivity.kt"
        )
        val exporter = source(
            "app/src/main/java/com/wisso/wizefiles/feature/appmanager/AppBackupExporter.kt"
        )

        assertTrue("base.apk" in exporter)
        assertTrue("metadata.json" in exporter)
        assertTrue("SHA-256" in exporter)
        assertTrue("\"apks\" else \"apk\"" in exporter)
        assertTrue("Intent.FLAG_GRANT_READ_URI_PERMISSION" in activity)
        assertTrue("Intent.ACTION_OPEN_DOCUMENT_TREE" in activity)
        assertTrue("FileOperationService.copy(" in activity)
    }

    private fun source(path: String): String = File(root, path).readText()
}
