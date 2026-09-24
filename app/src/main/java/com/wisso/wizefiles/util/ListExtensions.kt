// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

fun <T> List<T>.startsWith(prefix: List<T>): Boolean =
    size >= prefix.size && subList(0, prefix.size) == prefix

fun <T> List<T>.endsWith(suffix: List<T>): Boolean =
    size >= suffix.size && subList(size - suffix.size, size) == suffix
