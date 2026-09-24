package com.wisso.wizefiles.core.android.compat

import android.content.Context
import android.os.Build
import android.system.ErrnoException
import android.system.OsConstants
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AndroidCompatServicesRobolectricTest {
    @Test
    fun unsupportedProxyOperationsExposeErrno() {
        val callback = object : ProxyFileDescriptorCallbackCompat() {
            override fun onRelease() = Unit
        }

        val failure = assertThrows(ErrnoException::class.java) { callback.onGetSize() }
        assertEquals(OsConstants.EBADF, failure.errno)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun platformProxyAdapterForwardsEveryCallback() {
        val released = AtomicBoolean()
        val callback = object : ProxyFileDescriptorCallbackCompat() {
            override fun onGetSize(): Long = 42
            override fun onRead(offset: Long, size: Int, data: ByteArray): Int = 3
            override fun onWrite(offset: Long, size: Int, data: ByteArray): Int = 4
            override fun onFsync() = Unit
            override fun onRelease() { released.set(true) }
        }.toProxyFileDescriptorCallback()

        assertEquals(42, callback.onGetSize())
        assertEquals(3, callback.onRead(0, 8, ByteArray(8)))
        assertEquals(4, callback.onWrite(0, 8, ByteArray(8)))
        callback.onFsync()
        callback.onRelease()
        assertTrue(released.get())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun bundledLocaleConfigurationIsReadableOnApi30() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val config = LocaleConfigCompat(context)

        assertEquals(LocaleConfigCompat.STATUS_SUCCESS, config.status)
        val locales = checkNotNull(config.supportedLocales)
        assertTrue(locales.size() > 0)
        assertTrue(
            (0 until locales.size()).any { index ->
                locales[index]?.toLanguageTag() == "en-US"
            }
        )
    }
}
