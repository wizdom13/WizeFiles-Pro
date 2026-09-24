package com.wisso.wizefiles.util

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

val backgroundExecutor: ExecutorService by lazy {
    Executors.newCachedThreadPool()
}
