package com.wisso.wizefiles.provider.common

import java.nio.file.Path

class PollingWatchKey(
    watchService: PollingWatchService,
    path: Path
) : AbstractWatchKey<PollingWatchKey, Path>(watchService, path)
