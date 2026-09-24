package com.wisso.wizefiles.storage.local

import com.wisso.wizefiles.storage.FileOperationRequest
import com.wisso.wizefiles.storage.OperationResult
import com.wisso.wizefiles.storage.PreservationStatus
import com.wisso.wizefiles.storage.ProviderOperationRunner
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Outcome tests against the concrete system NIO adapter, not a recording fake. */
class LocalProviderMutationConformanceTest {
    @Test
    fun `real adapter copy move and delete preserve filesystem outcomes`() {
        val root = Files.createTempDirectory("wize-local-provider")
        try {
            val source = Files.write(root.resolve("source.txt"), "provider contract".toByteArray())
            val copied = root.resolve("copied.txt")
            val moved = root.resolve("moved.txt")

            val copyResult = ProviderOperationRunner.run(
                LocalProviderMutationPort(options = LocalMutationOptions(copyAttributes = true)),
                FileOperationRequest.Copy(source.node(), copied.node())
            ) as OperationResult.Complete
            assertEquals(Files.size(source), copyResult.completedBytes)
            assertTrue(copyResult.metadata.entries.all { it.status == PreservationStatus.PRESERVED })
            assertEquals("provider contract", String(Files.readAllBytes(copied)))

            ProviderOperationRunner.run(
                LocalProviderMutationPort(),
                FileOperationRequest.Move(copied.node(), moved.node())
            )
            assertFalse(Files.exists(copied))
            assertTrue(Files.exists(moved))

            ProviderOperationRunner.run(
                LocalProviderMutationPort(),
                FileOperationRequest.Delete(moved.node())
            )
            assertFalse(Files.exists(moved))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun Path.node() = LocalPathNode(this)
}
