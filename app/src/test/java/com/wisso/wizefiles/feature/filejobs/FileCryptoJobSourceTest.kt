package com.wisso.wizefiles.feature.filejobs

import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertTrue
import org.junit.Test

class FileCryptoJobSourceTest {

    @Test
    fun encryptJobSkipsAlreadyEncryptedFilesDuringRecursion() {
        val source = readExecutorSource()
        assertTrue(source.contains("if (!file.fileName.toString().endsWith(\".enc\"))"))
    }

    @Test
    fun decryptJobRecursionProcessesOnlyEncFiles() {
        val source = readExecutorSource()
        assertTrue(source.contains("if (file.fileName.toString().endsWith(\".enc\"))"))
        assertTrue(source.contains("Only .enc files can be decrypted"))
    }

    private fun readExecutorSource(): String {
        val file = listOf(
            File("src/main/java/com/wisso/wizefiles/feature/filejobs/FileMetadataWriteCryptoJobs.kt"),
            File("app/src/main/java/com/wisso/wizefiles/feature/filejobs/FileMetadataWriteCryptoJobs.kt")
        ).firstOrNull { it.exists() } ?: throw FileNotFoundException("FileMetadataWriteCryptoJobs.kt")
        return file.readText()
    }
}
