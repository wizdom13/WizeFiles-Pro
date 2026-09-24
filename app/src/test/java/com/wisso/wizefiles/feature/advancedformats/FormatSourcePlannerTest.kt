package com.wisso.wizefiles.feature.advancedformats

import com.wisso.wizefiles.feature.advancedformats.staging.FormatSourceAccess
import com.wisso.wizefiles.feature.advancedformats.staging.FormatSourceCapabilities
import com.wisso.wizefiles.feature.advancedformats.staging.FormatSourceDecision
import com.wisso.wizefiles.feature.advancedformats.staging.FormatSourcePlanner
import com.wisso.wizefiles.feature.advancedformats.staging.FormatSourceRejection
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatSourcePlannerTest {
    @Test
    fun `seekable single files stay descriptor backed regardless of size`() {
        val decision = FormatSourcePlanner.plan(
            FormatSourceCapabilities(
                seekable = true,
                requiresResourceBundle = false,
                reportedSizeBytes = Long.MAX_VALUE,
                availableBytes = 0
            )
        )

        assertEquals(
            FormatSourceDecision.Ready(FormatSourceAccess.DIRECT_DESCRIPTOR, 0),
            decision
        )
    }

    @Test
    fun `non seekable files and resource bundles are staged explicitly`() {
        val available = 2L * 1024L * 1024L * 1024L
        val size = 100L * 1024L * 1024L
        val single = FormatSourcePlanner.plan(
            FormatSourceCapabilities(false, false, size, available)
        ) as FormatSourceDecision.Ready
        val bundle = FormatSourcePlanner.plan(
            FormatSourceCapabilities(true, true, size, available)
        ) as FormatSourceDecision.Ready

        assertEquals(FormatSourceAccess.STAGE_SINGLE_FILE, single.access)
        assertEquals(FormatSourceAccess.STAGE_RESOURCE_BUNDLE, bundle.access)
        assertEquals(size, single.reservationBytes)
    }

    @Test
    fun `unknown sizes reserve bounded working space`() {
        val decision = FormatSourcePlanner.plan(
            FormatSourceCapabilities(
                seekable = false,
                requiresResourceBundle = false,
                reportedSizeBytes = null,
                availableBytes = Long.MAX_VALUE
            )
        ) as FormatSourceDecision.Ready

        assertEquals(FormatSourcePlanner.UNKNOWN_SIZE_RESERVATION_BYTES, decision.reservationBytes)
    }

    @Test
    fun `oversized and low space staging plans are rejected`() {
        val oversized = FormatSourcePlanner.plan(
            FormatSourceCapabilities(
                false,
                false,
                FormatSourcePlanner.MAX_STAGED_SOURCE_BYTES + 1,
                Long.MAX_VALUE
            )
        )
        val lowSpace = FormatSourcePlanner.plan(
            FormatSourceCapabilities(false, false, 100L * 1024L * 1024L, 120L * 1024L * 1024L)
        )

        assertEquals(
            FormatSourceDecision.Rejected(FormatSourceRejection.STAGING_LIMIT_EXCEEDED),
            oversized
        )
        assertEquals(
            FormatSourceDecision.Rejected(FormatSourceRejection.INSUFFICIENT_SPACE),
            lowSpace
        )
    }
}
