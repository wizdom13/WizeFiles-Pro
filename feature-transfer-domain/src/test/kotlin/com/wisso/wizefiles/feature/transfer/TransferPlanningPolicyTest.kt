package com.wisso.wizefiles.feature.transfer

import com.wisso.wizefiles.storage.ConflictPolicy
import com.wisso.wizefiles.storage.FileNode
import com.wisso.wizefiles.storage.FileOperationRequest
import com.wisso.wizefiles.storage.KeepBothNaming
import com.wisso.wizefiles.storage.MetadataAttribute
import com.wisso.wizefiles.storage.OperationCancellation
import com.wisso.wizefiles.storage.OperationCancelledException
import com.wisso.wizefiles.storage.ProviderCapabilities
import com.wisso.wizefiles.storage.ProviderMutationCapabilities
import com.wisso.wizefiles.storage.ProviderMutationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferPlanningPolicyTest {
    @Test
    fun `single multiple empty-directory and recursive requests preserve neutral identity`() {
        val requests = listOf(
            copy("source", "root", "target", "root"),
            copy("source", "root/empty", "target", "root/empty"),
            copy("source", "root/nested/file.txt", "target", "root/nested/file.txt")
        )

        val plan = plan(requests)

        assertEquals(requests, plan.requests)
        assertEquals("source", plan.requests.first().source.backendId)
        assertEquals("root/nested/file.txt", plan.requests.last().source.path)
    }

    @Test
    fun `all conflict policies are retained for adapter conflict execution`() {
        ConflictPolicy.entries.forEach { policy ->
            assertEquals(policy, plan(listOf(copy()), policy).conflictPolicy)
        }
    }

    @Test
    fun `keep both preserves file extensions and treats directories as leaf names`() {
        assertEquals("report (2).txt", KeepBothNaming.candidate("report.txt", 2))
        assertEquals("archive.tar (3).gz", KeepBothNaming.candidate("archive.tar.gz", 3))
        assertEquals("photos (2)", KeepBothNaming.candidate("photos", 2, isDirectory = true))
        assertThrows(IllegalArgumentException::class.java) { KeepBothNaming.candidate("../bad", 2) }
    }

    @Test
    fun `same logical resource and unsupported provider combinations are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            plan(listOf(FileOperationRequest.Copy(Node("same", "a"), Node("same", "a"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            plan(listOf(copy()), destination = provider(canWrite = false))
        }
        assertThrows(IllegalArgumentException::class.java) {
            plan(listOf(copy()), source = provider(canRead = false))
        }
        assertThrows(IllegalArgumentException::class.java) {
            plan(listOf(copy()), mutations = mutations(crossCopy = false))
        }
        assertThrows(IllegalArgumentException::class.java) {
            plan(
                listOf(FileOperationRequest.Move(Node("source", "a"), Node("target", "a"))),
                mutations = mutations(kinds = setOf(ProviderMutationKind.COPY))
            )
        }
    }

    @Test
    fun `metadata resume and atomic move requirements use capabilities`() {
        val requirements = TransferPlanningRequirements(
            metadataIntent = setOf(MetadataAttribute.MODIFIED_TIME),
            resumeRequired = true
        )
        val plan = plan(listOf(copy()), requirements = requirements)
        assertEquals(requirements.metadataIntent, plan.metadataIntent)
        assertTrue(plan.resumeRequired)

        assertThrows(IllegalArgumentException::class.java) {
            plan(listOf(copy()), requirements = requirements, mutations = mutations(resume = false))
        }
        assertThrows(IllegalArgumentException::class.java) {
            plan(
                listOf(FileOperationRequest.Move(Node("source", "a"), Node("source", "b"))),
                requirements = TransferPlanningRequirements(atomicMoveRequired = true),
                destination = provider(canMoveAtomically = false),
                mutations = mutations(kinds = setOf(ProviderMutationKind.MOVE), crossMove = true)
            )
        }
    }

    @Test
    fun `cancellation stops partial planning before another item is admitted`() {
        var checks = 0
        val cancellation = OperationCancellation { ++checks >= 3 }
        assertThrows(OperationCancelledException::class.java) {
            plan(List(10) { copy(sourcePath = "file-$it", targetPath = "file-$it") }, cancellation = cancellation)
        }
        assertEquals(3, checks)
    }

    @Test
    fun `large bounded plans are linear and excessive plans are rejected`() {
        val large = List(TransferPlanningPolicy.MAX_REQUESTS) {
            copy(sourcePath = "directory-${it / 100}/file-$it", targetPath = "directory-${it / 100}/file-$it")
        }
        assertEquals(TransferPlanningPolicy.MAX_REQUESTS, plan(large).requests.size)
        assertThrows(IllegalArgumentException::class.java) {
            plan(large.asSequence().plus(copy(sourcePath = "overflow", targetPath = "overflow")).asIterable())
        }
    }

    private fun plan(
        requests: Iterable<FileOperationRequest>,
        policy: ConflictPolicy = ConflictPolicy.KEEP_BOTH,
        source: ProviderCapabilities = provider(),
        destination: ProviderCapabilities = provider(),
        mutations: ProviderMutationCapabilities = mutations(),
        requirements: TransferPlanningRequirements = TransferPlanningRequirements(),
        cancellation: OperationCancellation = OperationCancellation.NONE
    ) = TransferPlanningPolicy.plan(
        requests, policy, source, destination, mutations, requirements, cancellation
    )

    private fun copy(
        sourceBackend: String = "source",
        sourcePath: String = "file.txt",
        targetBackend: String = "target",
        targetPath: String = "file.txt"
    ) = FileOperationRequest.Copy(
        Node(sourceBackend, sourcePath),
        Node(targetBackend, targetPath)
    )

    private fun provider(
        canRead: Boolean = true,
        canWrite: Boolean = true,
        canMoveAtomically: Boolean = true
    ) = ProviderCapabilities(canRead, canWrite, canMoveAtomically, false, true)

    private fun mutations(
        kinds: Set<ProviderMutationKind> = setOf(ProviderMutationKind.COPY, ProviderMutationKind.MOVE),
        resume: Boolean = true,
        crossCopy: Boolean = true,
        crossMove: Boolean = true
    ) = ProviderMutationCapabilities(kinds, resume, crossCopy, crossMove)

    private data class Node(override val backendId: String, override val path: String) : FileNode {
        override val name: String = path.substringAfterLast('/')
    }
}
