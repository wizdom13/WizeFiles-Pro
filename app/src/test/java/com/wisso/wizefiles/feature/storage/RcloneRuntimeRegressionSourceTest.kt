// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RcloneRuntimeRegressionSourceTest {
    @Test
    fun `dynamic Material input uses LinearLayout layout params`() {
        val wizard = sourceFile(
            "src/main/java/com/wisso/wizefiles/feature/storage/EditRcloneStorageFragment.kt"
        )
        val addDynamicField = wizard.substring(
            wizard.indexOf("private fun addDynamicField"),
            wizard.indexOf("private fun onConfigurationImported")
        )

        assertTrue(addDynamicField.contains("LinearLayout.LayoutParams("))
        assertFalse(addDynamicField.contains("input,\n            ViewGroup.LayoutParams("))
    }

    @Test
    fun `rclone receives private paths before native initialization`() {
        val engine = sourceFile(
            "src/main/java/com/wisso/wizefiles/data/providers/rclone/RcloneEngine.kt"
        )
        val bridge = sourceFile("../rclone-mobile/gomobile.go")

        assertTrue(engine.contains("Os.setenv(\"HOME\", runtimeDirectory.absolutePath, true)"))
        assertTrue(engine.contains("Os.setenv(\"XDG_CONFIG_HOME\", runtimeDirectory.absolutePath, true)"))
        assertTrue(engine.contains("Os.setenv(\"XDG_CACHE_HOME\", stagingDirectory.absolutePath, true)"))
        assertTrue(
            engine.contains(
                "Gomobile.rcloneInitialize(\n" +
                    "                configFile.absolutePath,\n" +
                    "                stagingDirectory.absolutePath"
            )
        )
        assertTrue(
            engine.indexOf("materializeConfiguration()") <
                engine.indexOf("initializeNative()", engine.indexOf("fun rpc("))
        )
        assertTrue(bridge.contains("config.SetConfigPath(configPath)"))
        assertTrue(bridge.contains("config.SetCacheDir(cacheDir)"))
        assertTrue(bridge.indexOf("config.SetConfigPath(configPath)") < bridge.indexOf("librclone.Initialize()"))
    }

    @Test
    fun `rclone keeps OAuth alive and avoids custom DNS and polling storm`() {
        val engine = sourceFile(
            "src/main/java/com/wisso/wizefiles/data/providers/rclone/RcloneEngine.kt"
        )
        val wizard = sourceFile(
            "src/main/java/com/wisso/wizefiles/feature/storage/EditRcloneStorageFragment.kt"
        )
        val service = sourceFile(
            "src/main/java/com/wisso/wizefiles/feature/storage/RcloneAuthorizationService.kt"
        )
        val authentication = sourceFile(
            "src/main/java/com/wisso/wizefiles/feature/storage/RcloneAuthenticationCoordinator.kt"
        )
        val manifest = sourceFile("src/main/AndroidManifest.xml")
        val provider = sourceFile(
            "src/main/java/com/wisso/wizefiles/data/providers/rclone/RcloneFileSystemProvider.kt"
        )
        val bridge = sourceFile("../rclone-mobile/gomobile.go")
        val buildScript = sourceFile("../scripts/build-rclone-aar.sh")

        assertFalse(buildScript.contains("-tags=netcgo"))
        assertFalse(engine.contains("DnsResolver"))
        assertFalse(engine.contains("setDNSQueryListener"))
        assertFalse(bridge.contains("DNSQueryListener"))
        assertFalse(bridge.contains("net.DefaultResolver"))
        assertTrue(service.contains("ServiceCompat.startForeground("))
        assertTrue(service.contains("FOREGROUND_SERVICE_TYPE_DATA_SYNC"))
        assertTrue(
            authentication.contains(
                "RcloneAuthorizationService.startAndAwait(context.applicationContext)"
            )
        )
        assertTrue(
            authentication.contains(
                "RcloneAuthorizationService.stop(context.applicationContext)"
            )
        )
        assertTrue(
            wizard.contains(
                "RcloneAuthenticationCoordinator.start(appContext, ::openOAuthURL)"
            )
        )
        assertTrue(wizard.contains("RcloneAuthenticationCoordinator.stop(appContext)"))
        assertTrue(
            wizard.indexOf(
                "RcloneAuthenticationCoordinator.start(appContext, ::openOAuthURL)"
            ) < wizard.indexOf(
                "RcloneEngine.continueCreateRemote(",
                wizard.indexOf("private suspend fun configureRemote")
            )
        )
        assertTrue(
            manifest.contains(
                "android:name=\"com.wisso.wizefiles.storage.RcloneAuthorizationService\""
            )
        )
        assertTrue(manifest.contains("android:foregroundServiceType=\"dataSync\""))
        assertFalse(provider.contains("FileSystemProvider(), PathObservableProvider"))
        assertFalse(provider.contains("WatchServicePathObservable"))
    }

    @Test
    fun `provider loading propagates lifecycle cancellation before touching fragment UI`() {
        val wizard = sourceFile(
            "src/main/java/com/wisso/wizefiles/feature/storage/EditRcloneStorageFragment.kt"
        )
        val loaderStart = wizard.indexOf("private fun loadProviderDefinitions()")
        val loaderEnd = wizard.indexOf("private fun refreshProviderChoices()", loaderStart)
        assertTrue(loaderStart >= 0)
        assertTrue(loaderEnd > loaderStart)
        val loader = wizard.substring(loaderStart, loaderEnd)

        assertTrue(loader.contains("catch (exception: CancellationException)"))
        assertTrue(loader.contains("throw exception"))
        assertTrue(loader.contains("if (!isAdded || view == null)"))
        assertTrue(
            loader.indexOf("catch (exception: CancellationException)") <
                loader.indexOf("showToast(")
        )
    }

    private fun sourceFile(path: String): String {
        val direct = File(path)
        if (direct.exists()) {
            return direct.readText()
        }
        val fromRepoRoot = File("app", path)
        if (fromRepoRoot.exists()) {
            return fromRepoRoot.readText()
        }
        throw java.io.FileNotFoundException(path)
    }
}
