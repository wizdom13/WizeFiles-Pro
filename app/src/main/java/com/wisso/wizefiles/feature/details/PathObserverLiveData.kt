// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.details

import com.wisso.wizefiles.feature.filebrowser.PathObserver
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.CloseableLiveData

abstract class PathObserverLiveData<T>(protected val path: AppPath) : CloseableLiveData<T>() {
    private var observer: PathObserver? = null

    @Volatile
    private var changedWhileInactive = false

    protected fun observe() {
        val legacyPath = path.toLegacyPathOrNull() ?: return
        observer = PathObserver(legacyPath) { onChangeObserved() }
    }

    abstract fun loadValue()

    private fun onChangeObserved() {
        if (hasActiveObservers()) {
            loadValue()
        } else {
            changedWhileInactive = true
        }
    }

    override fun onActive() {
        if (changedWhileInactive) {
            loadValue()
            changedWhileInactive = false
        }
    }

    override fun close() {
        observer?.close()
    }
}
