package com.wisso.wizefiles.feature.packageinstaller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageSplitSelectorTest {
    private val selector = PackageSplitSelector()

    @Test
    fun `selects matching abi density and locale while retaining feature splits`() {
        val apks = listOf(
            apk("base.apk", null),
            apk("split_config.arm64_v8a.apk", "config.arm64_v8a"),
            apk("split_config.x86_64.apk", "config.x86_64"),
            apk("split_config.xxhdpi.apk", "config.xxhdpi"),
            apk("split_config.xhdpi.apk", "config.xhdpi"),
            apk("split_config.en.apk", "config.en"),
            apk("split_config.fr.apk", "config.fr"),
            apk("feature_camera.apk", "feature.camera")
        )

        val result = selector.select(
            apks,
            DeviceApkConfiguration(listOf("arm64-v8a", "armeabi-v7a"), 440, listOf("en-US"))
        )

        assertEquals(
            setOf(
                "base.apk",
                "split_config.arm64_v8a.apk",
                "split_config.xxhdpi.apk",
                "split_config.en.apk",
                "feature_camera.apk"
            ),
            result.selected.mapTo(linkedSetOf(), PackageApk::entryName)
        )
        assertEquals(3, result.excluded.size)
    }

    @Test
    fun `fails when package has only unsupported abi splits`() {
        val failure = runCatching {
            selector.select(
                listOf(
                    apk("base.apk", null),
                    apk("split_config.x86.apk", "config.x86")
                ),
                DeviceApkConfiguration(listOf("arm64-v8a"), 420, listOf("en"))
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `requires exactly one base apk`() {
        val failure = runCatching {
            selector.select(
                listOf(apk("split_config.en.apk", "config.en")),
                DeviceApkConfiguration(listOf("arm64-v8a"), 420, listOf("en"))
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    private fun apk(entryName: String, splitName: String?) = PackageApk(
        entryName = entryName,
        splitName = splitName,
        sizeBytes = 1,
        sha256 = "00"
    )
}
