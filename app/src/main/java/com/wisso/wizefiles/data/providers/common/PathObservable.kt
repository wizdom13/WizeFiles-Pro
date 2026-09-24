// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import java.io.Closeable

interface PathObservable : Closeable {
    fun addObserver(observer: () -> Unit)

    fun removeObserver(observer: () -> Unit)
}
