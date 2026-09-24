package com.wisso.wizefiles.provider.rclone

import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RcloneStagingChannelOptionsTest {
    @Test
    fun `create new remote reuses the precreated local staging file`() {
        val stagingFile = Files.createTempFile("rclone-channel-", ".tmp")
        try {
            val localOptions = rcloneStagingChannelOptions(
                setOf(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            )

            assertFalse(StandardOpenOption.CREATE_NEW in localOptions)
            assertTrue(StandardOpenOption.CREATE in localOptions)
            assertTrue(StandardOpenOption.WRITE in localOptions)

            Files.newByteChannel(stagingFile, localOptions).use { channel ->
                assertEquals(2, channel.write(ByteBuffer.wrap("ok".toByteArray())))
            }
            assertEquals("ok", String(Files.readAllBytes(stagingFile)))
        } finally {
            Files.deleteIfExists(stagingFile)
        }
    }

    @Test
    fun `local staging options preserve append truncate and default read behavior`() {
        val appendOptions = rcloneStagingChannelOptions(
            setOf(StandardOpenOption.APPEND)
        )
        assertTrue(StandardOpenOption.APPEND in appendOptions)
        assertTrue(StandardOpenOption.WRITE in appendOptions)

        val truncateOptions = rcloneStagingChannelOptions(
            setOf(StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        )
        assertTrue(StandardOpenOption.TRUNCATE_EXISTING in truncateOptions)

        val readOptions = rcloneStagingChannelOptions(emptySet<OpenOption>())
        assertTrue(StandardOpenOption.READ in readOptions)
    }

    @Test
    fun `provider still enforces create new against the remote destination`() {
        val provider = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/data/providers/rclone/" +
                "RcloneFileSystemProvider.kt"
        )
        val channelMethod = provider.substring(
            provider.indexOf("override fun newByteChannel"),
            provider.indexOf("override fun newDirectoryStream")
        )

        assertTrue(
            channelMethod.contains(
                "StandardOpenOption.CREATE_NEW in options && existing != null"
            )
        )
        assertTrue(channelMethod.contains("throw FileAlreadyExistsException(path.toString())"))
    }

    private fun sourceFile(path: String): String {
        val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
            .first { File(it, "app/src/main/AndroidManifest.xml").isFile }
        return File(root, path).readText()
    }
}
