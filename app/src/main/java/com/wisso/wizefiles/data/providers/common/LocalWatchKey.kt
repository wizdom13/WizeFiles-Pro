// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import java.nio.file.Path
import java.nio.file.WatchEvent

class LocalWatchKey(
    watchService: LocalWatchService,
    path: Path,
    @Volatile
    internal var kinds: Set<WatchEvent.Kind<*>>
) : AbstractWatchKey<LocalWatchKey, Path>(watchService, path)
