package com.wisso.wizefiles.feature.about

data class FaqItem(
    val question: String,
    val answer: String,
    val isHighlighted: Boolean = false
)
