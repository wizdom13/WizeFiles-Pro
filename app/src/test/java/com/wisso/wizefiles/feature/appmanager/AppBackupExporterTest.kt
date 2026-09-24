package com.wisso.wizefiles.feature.appmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppBackupExporterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val generatedSessions = mutableListOf<File>()
    private val exporter = AppBackupExporter(context) { _, _ -> }

    @After
    fun tearDown() {
        generatedSessions.forEach(File::deleteRecursively)
    }

    @Test
    fun `single package is copied as the requested apk filename`() = runTest {
        val source = temporaryFolder.newFile("base.apk").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        val backup = exporter.export(
            listOf(app("Adobe Acrobat", "26.7.1.47181", listOf(source.absolutePath))),
            retainedForTransfer = false
        ).single().also { generatedSessions += it.file.parentFile!! }

        assertEquals("Adobe_Acrobat_26.7.1.47181.apk", backup.file.name)
        assertEquals(AppBackupExporter.MIME_APK, backup.mimeType)
        assertTrue(source.readBytes().contentEquals(backup.file.readBytes()))
    }

    @Test
    fun `split package contains base all splits and checksum metadata`() = runTest {
        val base = temporaryFolder.newFile("base.apk").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val split = temporaryFolder.newFile("split_config.en.apk").apply {
            writeBytes(byteArrayOf(4, 5, 6))
        }

        val backup = exporter.export(
            listOf(app("Maps", "9.5", listOf(base.absolutePath, split.absolutePath))),
            retainedForTransfer = false
        ).single().also { generatedSessions += it.file.parentFile!! }

        assertEquals("Maps_9.5.apks", backup.file.name)
        assertEquals(AppBackupExporter.MIME_APKS, backup.mimeType)
        ZipFile(backup.file).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue("base.apk" in names)
            assertTrue("split_config.en.apk" in names)
            assertTrue("metadata.json" in names)
            val metadata = JSONObject(
                zip.getInputStream(zip.getEntry("metadata.json")).bufferedReader().readText()
            )
            assertEquals("com.example.test", metadata.getString("packageName"))
            assertEquals(2, metadata.getJSONArray("apks").length())
            repeat(metadata.getJSONArray("apks").length()) { index ->
                assertEquals(64, metadata.getJSONArray("apks").getJSONObject(index)
                    .getString("sha256").length)
            }
        }
    }

    @Test
    fun `filename sanitization preserves extension contract and avoids collisions`() {
        assertEquals(
            "App_Name_42.apk",
            appBackupFileName(" App / Name ", "", 42, "com.example.app", false)
        )
        assertEquals(
            "App_1.0.apks",
            appBackupFileName("App", "1.0", 1, "com.example.app", true)
        )
        val used = linkedSetOf("App_1.0.apk")
        assertEquals("App_1.0_2.apk", uniqueBackupFileName("App_1.0.apk", used))
    }

    @Test
    fun `share sessions expire after one day while transfer sessions are retained`() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val shareSession = AppBackupCache.createSession(cacheDir, retainedForTransfer = false)
        val transferSession = AppBackupCache.createSession(cacheDir, retainedForTransfer = true)
        val now = System.currentTimeMillis()
        val twentyFiveHoursAgo = now - TimeUnit.HOURS.toMillis(25)
        shareSession.setLastModified(twentyFiveHoursAgo)
        transferSession.setLastModified(twentyFiveHoursAgo)

        AppBackupCache.cleanupExpired(cacheDir, now)

        assertFalse(shareSession.exists())
        assertTrue(transferSession.exists())
    }

    private fun app(label: String, version: String, sources: List<String>) = InstalledApp(
        packageName = "com.example.test",
        label = label,
        versionName = version,
        versionCode = 42,
        isSystem = false,
        isEnabled = true,
        isSplit = sources.size > 1,
        totalApkBytes = sources.sumOf { File(it).length() },
        firstInstallTimeMillis = 1,
        lastUpdateTimeMillis = 2,
        sourceApkPaths = sources,
        hasLaunchIntent = true
    )
}
