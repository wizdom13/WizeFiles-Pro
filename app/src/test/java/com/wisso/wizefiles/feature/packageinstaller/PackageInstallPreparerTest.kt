package com.wisso.wizefiles.feature.packageinstaller

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageInstallPreparerTest {
    @Test
    fun `groups repeated split verification errors and bounds examples`() {
        val message = summarizeSplitVerificationErrors(
            errors = listOf(
                "split_config.en.apk: Unable to read APK package metadata",
                "split_config.fr.apk: Unable to read APK package metadata",
                "split_config.de.apk: Unable to read APK package metadata",
                "split_config.ar.apk: Unable to read APK package metadata",
                "Not every APK in the container passed verification"
            ),
            passedApks = 1,
            totalApks = 5
        )

        assertTrue(message.startsWith("1 of 5 APKs passed verification."))
        assertTrue(message.contains("4 APKs: Unable to read APK package metadata"))
        assertTrue(message.contains("+1 more"))
        assertFalse(message.contains("split_config.ar.apk"))
    }
}
