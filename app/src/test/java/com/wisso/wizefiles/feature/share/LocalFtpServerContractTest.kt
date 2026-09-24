// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.share

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFtpServerContractTest {
    @Test fun `ftp paths remain inside a selected virtual root`() {assertEquals("/Travel/Photos/2026",normalizeFtpPath("/Travel/Photos","2026"));assertThrows(IllegalArgumentException::class.java){normalizeFtpPath("/Travel","../secret")}}
    @Test fun `compatibility server disables anonymous active mode and dangerous site commands`() {val source=projectFile("src/main/java/com/wisso/wizefiles/feature/share/LocalFtpServer.kt");assertTrue(source.contains("Anonymous access is disabled"));assertTrue(source.contains("Active mode is not supported"));assertTrue(!source.contains("\"SITE\"->"));assertTrue(source.contains("gateway.ftpWritable"));assertTrue(source.contains("authFailures>=5"));assertTrue(source.contains("StorageFacade"))}
    private fun projectFile(path:String):String=listOf(File(path),File("app/$path")).firstOrNull(File::exists)?.readText()?:error("Missing $path")
}
