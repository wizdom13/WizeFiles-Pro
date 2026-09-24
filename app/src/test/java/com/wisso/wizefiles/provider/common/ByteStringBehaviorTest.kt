// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ByteStringBehaviorTest {

    @Test
    fun factoriesMakeOwnershipExplicit() {
        val copiedSource = byteArrayOf(1, 2)
        val copied = ByteString.fromBytes(copiedSource)
        copiedSource[0] = 9
        assertArrayEquals(byteArrayOf(1, 2), copied.toBytes())

        val ownedSource = byteArrayOf(3, 4)
        val owned = ByteString.takeBytes(ownedSource)
        ownedSource[0] = 8
        assertArrayEquals(byteArrayOf(8, 4), owned.toBytes())
    }

    @Test
    fun substringSearchIncludesWholeValueAndTerminalMatches() {
        val value = "abcabc".toByteString()

        assertEquals(0, value.indexOf("abc".toByteString()))
        assertEquals(3, value.indexOf("abc".toByteString(), fromIndex = 1))
        assertEquals(3, value.lastIndexOf("abc".toByteString()))
        assertTrue(value.contains("abc".toByteString()))
        assertTrue(value.startsWith("abc".toByteString()))
        assertTrue(value.endsWith("abc".toByteString()))
    }

    @Test
    fun slicingAndSplittingRetainEmptySegments() {
        val value = "/a/".toByteString()

        assertEquals(listOf("", "a", ""), value.split("/".toByteString()).map(ByteString::toString))
        assertEquals("a/", value.drop(1).toString())
        assertEquals("/a", value.dropLast(1).toString())
        assertEquals("a", value.dropWhile { it == '/'.code.toByte() }.dropLastWhile {
            it == '/'.code.toByte()
        }.toString())
    }

    @Test
    fun delimiterHelpersHonorMissingFallbacks() {
        val value = "name.ext".toByteString()
        val fallback = "fallback".toByteString()

        assertEquals("name", value.substringBefore('.'.code.toByte()).toString())
        assertEquals("ext", value.substringAfterLast('.'.code.toByte()).toString())
        assertSame(fallback, value.substringAfter('/'.code.toByte(), fallback))
    }

    @Test
    fun concatenationComparisonAndCStringAreStable() {
        val left = "ab".toByteString()
        val combined = left + "cd".toByteString()

        assertEquals("abcd", combined.toString())
        assertTrue(left < combined)
        assertArrayEquals(byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 0), left.cstr)
        assertSame(left, left + ByteString.EMPTY)
        assertNotSame(left, combined)
    }

    @Test
    fun invalidSlicesAndNegativeCountsFail() {
        val value = "abc".toByteString()

        assertThrows(IndexOutOfBoundsException::class.java) { value.substring(-1) }
        assertThrows(IndexOutOfBoundsException::class.java) { value.substring(2, 1) }
        assertThrows(IllegalArgumentException::class.java) { value.take(-1) }
        assertThrows(IllegalArgumentException::class.java) { value.split(ByteString.EMPTY) }
    }

    @Test
    fun builderGrowsAppendsAndCopiesItsResult() {
        val builder = ByteStringBuilder(1)
            .append('a'.code.toByte())
            .append("bc".toByteArray())

        assertFalse(builder.isEmpty)
        assertEquals(3, builder.length)
        assertTrue(builder.capacity() >= 3)
        assertEquals('b'.code.toByte(), builder[1])
        assertEquals("abc", builder.toString())

        val first = builder.toByteString()
        builder.append('d'.code.toByte())
        assertEquals("abc", first.toString())
        assertEquals("abcd", builder.toByteString().toString())
    }
}
