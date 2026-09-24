package com.wisso.wizefiles.core.files.provider.legacy

import android.net.Uri
import com.wisso.wizefiles.provider.legacy.installFileSystemProvider
import com.wisso.wizefiles.provider.rclone.RcloneFileSystemProvider
import com.wisso.wizefiles.provider.rclone.isRclonePath
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LegacyFileProviderBridgePathTest {

    @Test
    fun encodedRcloneUriResolvesThroughRegisteredProviderWithoutNetworkAccess() {
        installFileSystemProvider(RcloneFileSystemProvider)
        val remoteUri = URI.create("rclone://issue-7/Documents/note.txt")
        val fileProviderUri = Uri.Builder()
            .scheme("content")
            .authority("com.wisso.wizefiles.test.file_provider")
            .encodedPath("/${Uri.encode(remoteUri.toString())}")
            .build()

        val path = fileProviderUri.toLegacyFileProviderPath()

        assertTrue(path.isRclonePath)
        assertEquals(remoteUri, path.toUri())
    }
}
