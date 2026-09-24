package com.wisso.wizefiles.core.files.provider.legacy

import android.os.ParcelFileDescriptor
import com.wisso.wizefiles.provider.ftp.FtpFileSystemProvider
import java.net.URI
import java.nio.file.StandardOpenOption
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyFileProviderBridgeOpenOptionsTest {

    @Test
    fun readOnlyModeMapsToReadOnlyOpenOptions() {
        val options = ParcelFileDescriptor.MODE_READ_ONLY.toLegacyOpenOptions()

        assertTrue(options.contains(StandardOpenOption.READ))
        assertFalse(options.contains(StandardOpenOption.WRITE))
    }

    @Test
    fun writeOnlyModeMapsToWriteOnlyOpenOptions() {
        val options = (
            ParcelFileDescriptor.MODE_WRITE_ONLY or
                ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE
            ).toLegacyOpenOptions()

        assertFalse(options.contains(StandardOpenOption.READ))
        assertTrue(options.contains(StandardOpenOption.WRITE))
    }

    @Test
    fun readWriteModeKeepsExplicitReadAndWriteOpenOptions() {
        val options = ParcelFileDescriptor.MODE_READ_WRITE.toLegacyOpenOptions()

        assertTrue(options.contains(StandardOpenOption.READ))
        assertTrue(options.contains(StandardOpenOption.WRITE))
    }

    @Test
    fun modeStringRwKeepsReadAndWriteOpenOptions() {
        val mode = "rw".toLegacyOpenMode()

        val options = mode.toLegacyOpenOptions()

        assertTrue(options.contains(StandardOpenOption.READ))
        assertTrue(options.contains(StandardOpenOption.WRITE))
    }

    @Test
    fun modeStringWMapsToWriteOnlyOpenOptions() {
        val mode = "w".toLegacyOpenMode()

        val options = mode.toLegacyOpenOptions()

        assertFalse(options.contains(StandardOpenOption.READ))
        assertTrue(options.contains(StandardOpenOption.WRITE))
    }

    @Test
    fun modeStringRMapsToReadOnlyOpenOptions() {
        val mode = "r".toLegacyOpenMode()

        val options = mode.toLegacyOpenOptions()

        assertTrue(options.contains(StandardOpenOption.READ))
        assertFalse(options.contains(StandardOpenOption.WRITE))
    }

    @Test
    fun readWriteModeCanDowngradeToReadOnlyForEffectivelyReadOnlyPath() {
        val coercedMode = coerceLegacyOpenMode(
            ParcelFileDescriptor.MODE_READ_WRITE,
            canRead = true,
            canWrite = false
        )
        val options = coercedMode.toLegacyOpenOptions()

        assertNotEquals(ParcelFileDescriptor.MODE_READ_WRITE, coercedMode)
        assertTrue(options.contains(StandardOpenOption.READ))
        assertFalse(options.contains(StandardOpenOption.WRITE))
    }

    @Test
    fun ftpPathDoesNotAttemptDirectFileOpen() {
        val ftpPath = FtpFileSystemProvider.getPath(URI.create("ftp://user@example.com/file.txt"))

        assertFalse(ftpPath.canOpenDirectly(ParcelFileDescriptor.MODE_READ_ONLY))
    }

    @Test
    fun ftpPathDoesNotCoerceLegacyModeViaToFile() {
        val ftpPath = FtpFileSystemProvider.getPath(URI.create("ftp://user@example.com/file.txt"))
        val mode = ParcelFileDescriptor.MODE_READ_WRITE

        val coercedMode = ftpPath.coerceLegacyOpenMode(mode)

        assertEquals(mode, coercedMode)
    }
}
