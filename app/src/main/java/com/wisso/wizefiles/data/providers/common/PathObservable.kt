package com.wisso.wizefiles.provider.common

import java.io.Closeable

interface PathObservable : Closeable {
    fun addObserver(observer: () -> Unit)

    fun removeObserver(observer: () -> Unit)
}
