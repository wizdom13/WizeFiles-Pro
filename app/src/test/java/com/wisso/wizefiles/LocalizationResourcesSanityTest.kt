// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationResourcesSanityTest {

    private val advertisedLocaleDirectories = listOf(
        "values-ar",
        "values-b+es+419",
        "values-de",
        "values-fr",
        "values-hi",
        "values-id",
        "values-pt-rBR",
        "values-ru",
        "values-th",
        "values-tr",
        "values-ur",
        "values-vi",
        "values-zh-rCN",
        "values-zh-rTW",
    )

    private fun localizedXmlFiles(): List<File> =
        advertisedLocaleDirectories.flatMap { directoryName ->
            val directory = File("src/main/res", directoryName)
            assertTrue("Missing locale directory: $directory", directory.isDirectory)
            directory.listFiles { file -> file.isFile && file.extension == "xml" }
                ?.sortedBy { it.name }
                .orEmpty()
        }

    @Test
    fun localizedStringsDoNotContainTemplatePlaceholders() {
        localizedXmlFiles().forEach { file ->
            val content = file.readText()
            assertFalse("Found {str} placeholder in $file", content.contains("{str}"))
            assertFalse(
                "Found template marker in $file",
                content.contains("{{") || content.contains("}}"),
            )
        }
    }

    @Test
    fun localizedStringsDoNotContainInvalidUnicodeEscapes() {
        val invalidUnicode = Regex("\\\\u(?![0-9a-fA-F]{4})")
        localizedXmlFiles().forEach { file ->
            val content = file.readText()
            assertFalse(
                "Found invalid Unicode escape in $file",
                invalidUnicode.containsMatchIn(content),
            )
        }
    }

    @Test
    fun frenchArrayEntriesDoNotUseRawAsciiApostrophes() {
        val pattern = Regex("<item>(.*?)</item>")
        val content = File("src/main/res/values-fr/arrays.xml").readText()
        pattern.findAll(content).forEach { match ->
            assertFalse(
                "Found raw ASCII apostrophe in values-fr/arrays.xml item: ${match.value}",
                match.groupValues[1].contains("'"),
            )
        }
    }
}
