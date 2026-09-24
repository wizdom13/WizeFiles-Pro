package com.wisso.wizefiles.provider.common

import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertTrue
import org.junit.Test

class ForeignCopyMoveDirectorySourceTest {

    @Test
    fun move_handlesDirectoriesRecursively() {
        val source = readSource()

        assertTrue(source.contains("sourceAttributes.isDirectory"))
        assertTrue(source.contains("moveDirectoryRecursively("))
        assertTrue(source.contains("source.newDirectoryStream().use"))
        assertTrue(source.contains("move(child, target.resolve(child.fileName.toString()), *optionsForCopy)"))
        assertTrue(source.contains("deleteSourceAfterForeignMove(source, target)"))
    }

    private fun readSource(): String {
        val file = listOf(
            File("src/main/java/com/wisso/wizefiles/data/providers/common/ForeignCopyMove.kt"),
            File("app/src/main/java/com/wisso/wizefiles/data/providers/common/ForeignCopyMove.kt")
        ).firstOrNull { it.exists() } ?: throw FileNotFoundException("ForeignCopyMove.kt")
        return file.readText()
    }
}
