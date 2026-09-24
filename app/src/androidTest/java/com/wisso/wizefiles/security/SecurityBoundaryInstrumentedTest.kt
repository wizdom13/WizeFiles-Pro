package com.wisso.wizefiles.security

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wisso.wizefiles.feature.audioplayer.AudioPlaybackService
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import com.wisso.wizefiles.feature.nearby.NearbyTransferService
import com.wisso.wizefiles.storage.NearbySessionEvent
import com.wisso.wizefiles.storage.NearbySessionReducer
import com.wisso.wizefiles.storage.NearbySessionState
import com.wisso.wizefiles.storage.SecureRelativePath
import com.wisso.wizefiles.storage.VaultLockEvent
import com.wisso.wizefiles.storage.VaultLockReducer
import com.wisso.wizefiles.storage.VaultLockState
import com.wisso.wizefiles.vault.VaultActivity
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurityBoundaryInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun fileProviderRequiresExplicitUriGrants() {
        val target = instrumentation.targetContext
        val provider = target.packageManager.resolveContentProvider(
            "${target.packageName}.file_provider",
            0
        )
        assertNotNull(provider)
        assertFalse(provider!!.exported)
        assertTrue(provider.grantUriPermissions)
    }

    @Test
    fun privilegedComponentsRemainUnexported() {
        val packageManager = instrumentation.targetContext.packageManager
        assertTrue(activityInfo(packageManager, FileListActivity::class.java).exported)
        assertFalse(activityInfo(packageManager, VaultActivity::class.java).exported)
        assertFalse(serviceInfo(packageManager, NearbyTransferService::class.java).exported)
    }

    @Test
    fun archiveAndNearbyHostilePathsFailAtTheSharedBoundary() {
        listOf("../escape", "/absolute", "C:/drive", "safe/../../escape", "a\\b").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) { SecureRelativePath.validate(path) }
        }
    }

    @Test
    fun nearbyPeerCannotBypassAuthenticationAndVaultRelocksAfterBackgroundDeadline() {
        assertThrows(IllegalArgumentException::class.java) {
            NearbySessionReducer.reduce(NearbySessionState.IDLE, NearbySessionEvent.TransferStarted)
        }
        var vault = VaultLockReducer.reduce(VaultLockState.LOCKED, VaultLockEvent.UnlockSucceeded)
        vault = VaultLockReducer.reduce(vault, VaultLockEvent.AppBackgrounded)
        vault = VaultLockReducer.reduce(vault, VaultLockEvent.RelockDeadlineReached)
        assertEquals(VaultLockState.LOCKED, vault)
    }

    @Test
    fun untrustedCrossPackageMediaControllerIsRejected() {
        val external = instrumentation.context
        val token = SessionToken(
            external,
            ComponentName(instrumentation.targetContext, AudioPlaybackService::class.java)
        )
        val future = MediaController.Builder(external, token).buildAsync()
        try {
            assertThrows(Exception::class.java) { future.get(5, TimeUnit.SECONDS) }
        } finally {
            future.cancel(true)
        }
    }

    @Suppress("DEPRECATION")
    private fun activityInfo(packageManager: PackageManager, type: Class<*>): ActivityInfo {
        val component = ComponentName(instrumentation.targetContext, type)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
        } else {
            packageManager.getActivityInfo(component, 0)
        }
    }

    @Suppress("DEPRECATION")
    private fun serviceInfo(packageManager: PackageManager, type: Class<*>): ServiceInfo {
        val component = ComponentName(instrumentation.targetContext, type)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getServiceInfo(component, PackageManager.ComponentInfoFlags.of(0))
        } else {
            packageManager.getServiceInfo(component, 0)
        }
    }
}
