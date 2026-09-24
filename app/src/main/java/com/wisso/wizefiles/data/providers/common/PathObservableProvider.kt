package com.wisso.wizefiles.provider.common

import java.nio.file.Path
import java.io.IOException

interface PathObservableProvider {
    @Throws(IOException::class)
    fun observe(path: Path, intervalMillis: Long): PathObservable
}
