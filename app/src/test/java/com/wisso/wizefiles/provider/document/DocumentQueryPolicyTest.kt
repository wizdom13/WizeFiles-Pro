// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.document

import com.wisso.wizefiles.provider.document.resolver.DocumentQueryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentQueryPolicyTest {
    @Test fun `provider errors win over loading state and are bounded`() {
        val decision = DocumentQueryPolicy.decide(true, "x".repeat(5000), 0)
        assertTrue(decision is DocumentQueryPolicy.Decision.Fail)
        assertEquals(DocumentQueryPolicy.MAX_PROVIDER_MESSAGE_LENGTH, (decision as DocumentQueryPolicy.Decision.Fail).message.length)
    }

    @Test fun `delayed results have a finite retry budget`() {
        assertEquals(DocumentQueryPolicy.Decision.WaitAndRetry, DocumentQueryPolicy.decide(true, null, 0))
        assertTrue(DocumentQueryPolicy.decide(true, null, DocumentQueryPolicy.MAX_LOADING_REFRESHES) is DocumentQueryPolicy.Decision.Fail)
    }

    @Test fun `ordinary results are consumed`() {
        assertEquals(DocumentQueryPolicy.Decision.ConsumeRows, DocumentQueryPolicy.decide(false, null, 0))
    }

    @Test fun `hostile refresh counts and blank diagnostics fail predictably`() {
        assertEquals(DocumentQueryPolicy.Decision.WaitAndRetry, DocumentQueryPolicy.decide(true, null, -1))
        assertTrue(
            DocumentQueryPolicy.decide(true, null, Int.MAX_VALUE) is DocumentQueryPolicy.Decision.Fail
        )
        assertEquals(DocumentQueryPolicy.Decision.ConsumeRows, DocumentQueryPolicy.decide(false, "   ", 0))
    }
}
