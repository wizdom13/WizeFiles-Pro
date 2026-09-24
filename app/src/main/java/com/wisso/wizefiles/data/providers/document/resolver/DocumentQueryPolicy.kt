// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.document.resolver

/** Pure policy for interpreting hostile or inconsistent DocumentsProvider query metadata. */
internal object DocumentQueryPolicy {
    const val MAX_LOADING_REFRESHES = 32
    const val MAX_PROVIDER_MESSAGE_LENGTH = 1024

    sealed interface Decision {
        data object ConsumeRows : Decision
        data object WaitAndRetry : Decision
        data class Fail(val message: String) : Decision
    }

    fun decide(loading: Boolean, error: String?, refreshCount: Int): Decision {
        val safeError = error?.trim()?.take(MAX_PROVIDER_MESSAGE_LENGTH)
        if (!safeError.isNullOrEmpty()) return Decision.Fail(safeError)
        if (!loading) return Decision.ConsumeRows
        return if (refreshCount >= MAX_LOADING_REFRESHES) {
            Decision.Fail("Document provider returned too many delayed responses")
        } else {
            Decision.WaitAndRetry
        }
    }
}
