package com.wisso.wizefiles.searchindex

import org.junit.Ignore
import org.junit.Test

class SearchIndexPerformanceBenchmarkTest {
    @Ignore("Manual device benchmark: records wall time and database size for release profiling")
    @Test
    fun `benchmark 500000 indexed entries`() = benchmark(500_000)

    @Ignore("Manual device benchmark: records wall time and database size for release profiling")
    @Test
    fun `benchmark 1000000 indexed entries`() = benchmark(1_000_000)

    private fun benchmark(count: Int) {
        val root = "/storage/emulated/0"
        SearchIndexDatabase.clear()
        SearchIndexDatabase.beginRootScan(root, 1L)
        (0 until count).asSequence().chunked(750).forEach { numbers ->
            SearchIndexDatabase.upsertBatch(
                numbers.map { number ->
                    SearchIndexRecord(
                        root,
                        "$root/Benchmark/folder-${number / 1000}/annual-report-$number.pdf",
                        "$root/Benchmark/folder-${number / 1000}",
                        "annual-report-$number.pdf",
                        false,
                        1024L,
                        number.toLong(),
                        false,
                        "application/pdf",
                        1L
                    )
                }
            )
        }
        SearchIndexDatabase.completeRootScan(root, 1L, count.toLong())
        check(SearchIndexDatabase.search(root, root, "report", true, 200, 0).size == 200)
    }
}
