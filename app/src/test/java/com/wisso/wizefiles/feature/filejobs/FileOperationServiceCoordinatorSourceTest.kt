package com.wisso.wizefiles.feature.filejobs

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationServiceCoordinatorSourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `service keeps public commands while coordinators own implementation`() {
        val service = source("FileOperationService.kt")
        val commands = source("FileOperationCommandCoordinator.kt")
        val signing = source("PackageSigningCoordinator.kt")
        val runtime = source("FileOperationRuntime.kt")

        assertTrue("internal fun enqueue" in service)
        assertTrue("FileOperationCommandCoordinator.archive" in service)
        assertTrue("PackageSigningCoordinator.signApk" in service)
        assertTrue("ArchiveRewriteEngine" in commands)
        assertTrue("TransferRepository.enqueue" in commands)
        assertTrue("ApkSigningSecretRegistry" in signing)
        assertTrue("TransferOperationType.APK_SIGN" in signing)
        assertTrue("FileOperationService.resumeTransfer" in signing)
        assertTrue("runtime.start(job)" in service)
        assertTrue("LongRunningOperationLimiter.acquire()" in runtime)
        assertTrue("FOREGROUND_SERVICE_TIMEOUT" in runtime)
    }

    private fun source(name: String): String = File(
        root,
        "app/src/main/java/com/wisso/wizefiles/feature/filejobs/$name"
    ).readText()
}
