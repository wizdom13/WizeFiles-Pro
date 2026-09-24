package com.wisso.wizefiles.feature.packageinstaller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidPackageInstallerInputTest {
    @Test
    fun `recognizes every supported installer extension case-insensitively`() {
        assertEquals("apk", AndroidPackageInstallerInput.extension("base.APK"))
        assertEquals("apks", AndroidPackageInstallerInput.extension("bundle.apks"))
        assertEquals("apkm", AndroidPackageInstallerInput.extension("mirror.APKM"))
        assertEquals("xapk", AndroidPackageInstallerInput.extension("game.xapk"))
    }

    @Test
    fun `rejects unsupported and misleading names`() {
        assertNull(AndroidPackageInstallerInput.extension("bundle.aab"))
        assertNull(AndroidPackageInstallerInput.extension("game.xapk.zip"))
        assertNull(AndroidPackageInstallerInput.extension("apk"))
    }
}
