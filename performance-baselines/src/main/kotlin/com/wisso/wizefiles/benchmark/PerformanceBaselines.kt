package com.wisso.wizefiles.benchmark

import com.wisso.wizefiles.storage.ConflictPolicy
import com.wisso.wizefiles.storage.ConflictKind
import com.wisso.wizefiles.storage.ConflictResolver
import com.wisso.wizefiles.storage.FileNode
import com.wisso.wizefiles.storage.FileOperationRequest
import com.wisso.wizefiles.storage.NearbySessionEvent
import com.wisso.wizefiles.storage.NearbySessionReducer
import com.wisso.wizefiles.storage.NearbySessionState
import com.wisso.wizefiles.storage.SecureRelativePath
import com.wisso.wizefiles.storage.TransferPlan
import com.wisso.wizefiles.storage.VaultPayloadFrame
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.ExperimentalPathApi
import kotlin.system.measureNanoTime
import java.lang.management.ManagementFactory
import java.nio.file.Paths
import java.util.Locale

private data class Result(
    val scenario: String,
    val items: Int,
    val medianMillis: Double,
    val tailMillis: Double,
    val medianAllocatedBytes: Long
)

fun main() {
    val results = listOf(
        benchmark("harness.collection-enumeration", 10_000) { input -> input.asSequence().toList() },
        benchmark("harness.collection-sort", 10_000) { input -> input.sortedDescending() },
        benchmark("harness.model-allocation", 10_000) { input -> input.mapIndexed { index, name -> "$index:$name" } },
        benchmark("search.initial-index", 100_000) { input -> input.associateBy(String::lowercase) },
        benchmark("search.query", 100_000) { input -> input.filter { "999" in it } },
        benchmark("search.incremental-update", 10_000) { input -> input.toMutableSet().apply { removeAll(input.take(100).toSet()); addAll(input.take(100).map { "$it-new" }) } },
        benchmark("recursive.scan", 10_000) { input -> input.sumOf { it.length } },
        benchmark("transfer.planning", 10_000) { input -> transferPlan(input) },
        benchmark("conflict.resolution", 10_000) { input ->
            input.forEach { ConflictResolver.decide(ConflictPolicy.KEEP_BOTH, ConflictKind.SAME_TYPE, "$it-copy") }
        },
        benchmark("remote.first-page", 100) { input -> input.asSequence().chunked(100).first() },
        benchmark("remote.enumeration", 10_000) { input -> input.chunked(100).flatten() },
        benchmark("thumbnail.cold-model", 10_000) { input -> input.associateWith { it.substringAfterLast('.') } },
        benchmark("thumbnail.warm-cache", 10_000) { input -> val cache = input.associateWith { it.length }; input.sumOf { cache.getValue(it) } },
        archiveBenchmark(10_000),
        benchmark("archive.filtered-list", 10_000) { input -> input.filter { it.endsWith("7.txt") } },
        benchmark("sync.planning", 10_000) { input -> transferPlan(input) },
        benchmark("vault.framing", 10_000) { input -> input.forEach { VaultPayloadFrame.decode(VaultPayloadFrame.encode(it.encodeToByteArray())) } },
        benchmark("vault.enumeration", 10_000) { input -> input.sorted() },
        benchmark("nearby.session-transitions", 10_000) { input -> input.forEach { nearbyRoundTrip() } }
    )
    val csv = buildString {
        appendLine("scenario,items,median_ms,tail_ms,median_allocated_bytes")
        results.forEach {
            appendLine("${it.scenario},${it.items},${"%.3f".format(Locale.ROOT, it.medianMillis)},${"%.3f".format(Locale.ROOT, it.tailMillis)},${it.medianAllocatedBytes}")
        }
    }
    System.getenv("WIZEFILES_BENCHMARK_OUTPUT")?.let { output ->
        Paths.get(output).toAbsolutePath().also { Files.createDirectories(it.parent) }.toFile().writeText(csv)
    } ?: print(csv)
}

private fun benchmark(name: String, size: Int, action: (List<String>) -> Any): Result {
    val input = List(size) { "directory-${it / 100}/file-$it.txt" }
    repeat(3) { action(input) }
    val allocationBean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
    val samples = List(20) {
        val before = allocationBean?.getThreadAllocatedBytes(Thread.currentThread().threadId()) ?: 0
        val nanos = measureNanoTime { action(input) }
        val allocated = (allocationBean?.getThreadAllocatedBytes(Thread.currentThread().threadId()) ?: before) - before
        nanos / 1_000_000.0 to allocated.coerceAtLeast(0)
    }
    val timings = samples.map { it.first }.sorted()
    val allocations = samples.map { it.second }.sorted()
    return Result(
        name,
        size,
        timings[timings.size / 2],
        timings[(timings.size * 95 / 100).coerceAtMost(timings.lastIndex)],
        allocations[allocations.size / 2]
    )
}

private fun transferPlan(input: List<String>): TransferPlan = TransferPlan(
    requests = input.map { name -> FileOperationRequest.Copy(Node("source", name), Node("target", name)) },
    conflictPolicy = ConflictPolicy.ASK
)

private data class Node(override val backendId: String, override val path: String) : FileNode {
    override val name: String get() = path.substringAfterLast('/')
}

private fun nearbyRoundTrip() {
    var state = NearbySessionReducer.reduce(NearbySessionState.IDLE, NearbySessionEvent.BeginDiscovery)
    state = NearbySessionReducer.reduce(state, NearbySessionEvent.PeerFound)
    state = NearbySessionReducer.reduce(state, NearbySessionEvent.AuthenticationAccepted)
    state = NearbySessionReducer.reduce(state, NearbySessionEvent.TransferStarted)
    NearbySessionReducer.reduce(state, NearbySessionEvent.TransferCompleted)
}

@OptIn(ExperimentalPathApi::class)
private fun archiveBenchmark(size: Int): Result {
    val directory = createTempDirectory("wizefiles-benchmark")
    val archive = directory.resolve("entries.zip")
    ZipOutputStream(Files.newOutputStream(archive)).use { output ->
        repeat(size) { index ->
            output.putNextEntry(ZipEntry(SecureRelativePath.validate("folder/entry-$index.txt")))
            output.closeEntry()
        }
    }
    repeat(3) { enumerateArchive(archive) }
    val allocationBean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
    val samples = List(20) {
        val before = allocationBean?.getThreadAllocatedBytes(Thread.currentThread().threadId()) ?: 0
        val millis = measureNanoTime { enumerateArchive(archive) } / 1_000_000.0
        val allocated = (allocationBean?.getThreadAllocatedBytes(Thread.currentThread().threadId()) ?: before) - before
        millis to allocated.coerceAtLeast(0)
    }
    directory.deleteRecursively()
    val timings = samples.map { it.first }.sorted()
    val allocations = samples.map { it.second }.sorted()
    return Result("archive.open-and-enumerate", size, timings[10], timings[19], allocations[10])
}

private fun enumerateArchive(path: java.nio.file.Path): Int {
    var count = 0
    ZipInputStream(Files.newInputStream(path)).use { input ->
        while (input.nextEntry != null) count++
    }
    return count
}
