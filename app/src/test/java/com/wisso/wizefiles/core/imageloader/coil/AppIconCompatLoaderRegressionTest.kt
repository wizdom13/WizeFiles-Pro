package com.wisso.wizefiles.core.imageloader.coil

import android.content.pm.ApplicationInfo
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconCompatLoaderRegressionTest {

    @Test
    fun appIconLoaderDependencyAndImportsAreRemoved() {
        val appBuildGradle = sourceFile("app/build.gradle", "build.gradle")
        val fetcher = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/core/imageloader/coil/AppIconFetcher.kt",
            "src/main/java/com/wisso/wizefiles/core/imageloader/coil/AppIconFetcher.kt"
        )
        val keyer = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/core/imageloader/coil/AppIconApplicationInfoFetcherFactory.kt",
            "src/main/java/com/wisso/wizefiles/core/imageloader/coil/AppIconApplicationInfoFetcherFactory.kt"
        )

        assertFalse(appBuildGradle.contains("me.zhanghai.android.appiconloader:appiconloader"))
        assertFalse(fetcher.contains("AppIconLoader"))
        assertFalse(keyer.contains("AppIconLoader"))
    }

    @Test
    fun keyTracksFieldsThatAffectIconIdentity() {
        val applicationInfo = ApplicationInfo().apply {
            packageName = "com.example.test"
            sourceDir = "/tmp/a.apk"
            publicSourceDir = "/tmp/a.apk"
            icon = 123
        }

        val baseKey = AppIconCompatLoader.getIconKey(applicationInfo)

        applicationInfo.icon = 456
        val versionChangedKey = AppIconCompatLoader.getIconKey(applicationInfo)

        assertTrue(baseKey.contains("com.example.test"))
        assertTrue(baseKey.contains("/tmp/a.apk"))
        assertTrue(baseKey.contains(":123"))
        assertFalse(baseKey == versionChangedKey)
    }

    private fun sourceFile(vararg candidates: String): String {
        val file = candidates.map(::File).firstOrNull { it.exists() }
            ?: throw java.io.FileNotFoundException(candidates.joinToString())
        return file.readText()
    }
}
