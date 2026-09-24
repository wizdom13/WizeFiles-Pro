// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.about

data class FaqItem(
    val question: String,
    val answer: String,
    val isHighlighted: Boolean = false
)
