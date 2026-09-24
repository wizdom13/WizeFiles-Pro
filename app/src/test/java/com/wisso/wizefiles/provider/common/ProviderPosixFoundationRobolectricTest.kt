package com.wisso.wizefiles.provider.common

import android.os.Parcel
import android.system.OsConstants
import java.nio.file.attribute.PosixFilePermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProviderPosixFoundationRobolectricTest {
    @Test
    fun modeConversionRoundTripsAllSystemBits() {
        val expected = PosixFileModeBit.entries.toSet()
        assertEquals(expected, PosixFileMode.fromInt(expected.toInt()))
    }

    @Test
    fun modeStringRendersSpecialExecuteBits() {
        val mode = setOf(
            PosixFileModeBit.OWNER_READ,
            PosixFileModeBit.OWNER_EXECUTE,
            PosixFileModeBit.SET_USER_ID,
            PosixFileModeBit.GROUP_WRITE,
            PosixFileModeBit.SET_GROUP_ID,
            PosixFileModeBit.OTHERS_EXECUTE,
            PosixFileModeBit.STICKY
        )
        assertEquals("r-s-wS--t", mode.toModeString())
    }

    @Test
    fun nioPermissionsRejectSpecialModeBits() {
        assertThrows(UnsupportedOperationException::class.java) {
            setOf(PosixFileModeBit.STICKY).toPermissions()
        }
        assertEquals(
            setOf(PosixFilePermission.OWNER_READ),
            setOf(PosixFileModeBit.OWNER_READ).toPermissions()
        )
    }

    @Test
    fun fileTypeUsesOnlyThePosixTypeMask() {
        val mode = OsConstants.S_IFREG or OsConstants.S_IRUSR or OsConstants.S_IWUSR
        assertEquals(PosixFileType.REGULAR_FILE, PosixFileType.fromMode(mode))
    }

    @Test
    fun parcelableModeUsesAStableIntegerMask() {
        val expected = setOf(
            PosixFileModeBit.OWNER_READ,
            PosixFileModeBit.OWNER_WRITE,
            PosixFileModeBit.GROUP_READ
        )
        val parcel = Parcel.obtain()
        val restored = try {
            expected.toParcelable().writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            ParcelablePosixFileMode.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
        assertEquals(expected, restored.value)
    }
}
