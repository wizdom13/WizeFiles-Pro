// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

val backgroundExecutor: ExecutorService by lazy {
    Executors.newCachedThreadPool()
}
