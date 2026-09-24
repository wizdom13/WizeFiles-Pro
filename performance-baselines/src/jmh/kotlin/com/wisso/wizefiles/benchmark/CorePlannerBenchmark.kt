package com.wisso.wizefiles.benchmark

import com.wisso.wizefiles.storage.ConflictPolicy
import com.wisso.wizefiles.storage.FileNode
import com.wisso.wizefiles.storage.FileOperationRequest
import com.wisso.wizefiles.storage.NearbySessionEvent
import com.wisso.wizefiles.storage.NearbySessionReducer
import com.wisso.wizefiles.storage.NearbySessionState
import com.wisso.wizefiles.storage.SecureRelativePath
import com.wisso.wizefiles.storage.TransferPlan
import com.wisso.wizefiles.storage.ProviderCapabilities
import com.wisso.wizefiles.storage.ProviderMutationCapabilities
import com.wisso.wizefiles.storage.ProviderMutationKind
import com.wisso.wizefiles.feature.transfer.TransferPlanningPolicy
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 20)
open class CorePlannerBenchmark {
    @Param("10000", "100000")
    var itemCount: Int = 0

    private lateinit var paths: List<String>
    private lateinit var transferRequests: List<FileOperationRequest>

    @Setup(Level.Trial)
    fun setup() {
        paths = List(itemCount) { "directory-${it / 100}/file-$it.txt" }
        transferRequests = paths.map { FileOperationRequest.Copy(Node("source", it), Node("target", it)) }
    }

    @Benchmark
    fun transferPlanning(): TransferPlan = TransferPlanningPolicy.plan(
        transferRequests,
        ConflictPolicy.ASK,
        READABLE_PROVIDER,
        WRITABLE_PROVIDER,
        COPY_CAPABILITIES
    )

    @Benchmark
    fun securePathValidation(): Int = paths.sumOf { SecureRelativePath.validate(it).length }

    @Benchmark
    fun nearbyTransitions(): NearbySessionState {
        var state = NearbySessionReducer.reduce(NearbySessionState.IDLE, NearbySessionEvent.BeginDiscovery)
        state = NearbySessionReducer.reduce(state, NearbySessionEvent.PeerFound)
        state = NearbySessionReducer.reduce(state, NearbySessionEvent.AuthenticationAccepted)
        state = NearbySessionReducer.reduce(state, NearbySessionEvent.TransferStarted)
        return NearbySessionReducer.reduce(state, NearbySessionEvent.TransferCompleted)
    }

    private data class Node(override val backendId: String, override val path: String) : FileNode {
        override val name: String get() = path.substringAfterLast('/')
    }

    private companion object {
        val READABLE_PROVIDER = ProviderCapabilities(true, false, false, false, true)
        val WRITABLE_PROVIDER = ProviderCapabilities(false, true, false, false, true)
        val COPY_CAPABILITIES = ProviderMutationCapabilities(
            setOf(ProviderMutationKind.COPY),
            supportsCrossProviderCopy = true
        )
    }
}
