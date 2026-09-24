package com.wisso.wizefiles.hiddenapi

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestrictedHiddenApiFallbackSourceTest {

    @Test
    fun themeCompatHasGracefulFallbackWhenHiddenApiIsUnavailable() {
        val source = sourceFile("src/main/java/com/wisso/wizefiles/core/android/compat/ContextCompat.kt")

        assertTrue(source.contains("runCatching { getThemeResIdMethod.invoke(this) as Int }"))
        assertTrue(source.contains("findActivityThemeResId() ?: applicationInfo.theme"))
    }

    @Test
    fun applicationInfoAndErrnoCompatHaveGracefulFallbacks() {
        val appInfoSource =
            sourceFile("src/main/java/com/wisso/wizefiles/core/android/compat/ApplicationInfoCompat.kt")
        val errnoSource =
            sourceFile("src/main/java/com/wisso/wizefiles/core/android/compat/ErrnoExceptionCompat.kt")

        assertTrue(appInfoSource.contains("getOrDefault(0L)"))
        assertFalse(appInfoSource.contains("lazyReflectedField"))
        assertTrue(errnoSource.contains("functionNameFromErrnoMessage(message)"))
        assertTrue(errnoSource.contains("else \"syscall\""))
        assertFalse(errnoSource.contains("getDeclaredField"))
        assertFalse(errnoSource.contains("isAccessible"))
        assertFalse(errnoSource.contains("lazyReflectedField"))
    }

    @Test
    fun stableUriParcelerFallsBackToReadStringWhenReadString8IsBlocked() {
        val source = sourceFile("src/main/java/com/wisso/wizefiles/util/StableUriParceler.kt")

        assertTrue(source.contains("runCatching { parcelReadString8Method.invoke(this) as String? }"))
        assertTrue(source.contains(".getOrElse { readString() }"))
    }

    @Test
    fun nioUtilsHasPublicFallbackAndExplicitRwHiddenApiBoundary() {
        val source = sourceFile("src/main/java/com/wisso/wizefiles/core/android/compat/NioUtilsCompat.kt")

        assertTrue(source.contains("newFileChannelPublicFallback"))
        assertTrue(source.contains("if (!readable || !writable)"))
        assertTrue(source.contains("AutoCloseOutputStream"))
        assertTrue(source.contains("AutoCloseInputStream"))
        assertTrue(source.contains("Read/write FileChannel from FileDescriptor requires hidden API"))
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
