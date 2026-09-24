// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

sealed interface Stateful<T> {
    val value: T?
}

data class Loading<T>(override val value: T?) : Stateful<T>

data class Failure<T>(
    override val value: T?,
    val throwable: Throwable
) : Stateful<T>

data class Success<T>(override val value: T) : Stateful<T>
