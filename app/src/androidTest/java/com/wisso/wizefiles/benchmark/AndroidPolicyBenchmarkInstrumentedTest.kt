package com.wisso.wizefiles.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wisso.wizefiles.feature.filebrowser.BrowserListPresentationPolicy
import com.wisso.wizefiles.feature.filebrowser.BrowserLoadPhase
import com.wisso.wizefiles.feature.filebrowser.FileListSubtitleCounts
import com.wisso.wizefiles.feature.nearby.NearbyProtocol
import com.wisso.wizefiles.feature.nearby.NearbyRole
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPolicyBenchmarkInstrumentedTest {
    @get:Rule val benchmarkRule = BenchmarkRule()

    @Test fun browserPresentationPreparation() = benchmarkRule.measureRepeated {
        BrowserListPresentationPolicy.decide(
            phase = BrowserLoadPhase.SUCCESS,
            counts = FileListSubtitleCounts(directories = 4_000, files = 6_000),
            isSearching = false
        )
    }

    @Test fun nearbyControlEncodeDecode() {
        val encoded = NearbyProtocol.hello("benchmark-session", NearbyRole.SEND, resumable = true)
        benchmarkRule.measureRepeated { NearbyProtocol.decode(encoded) }
    }
}
