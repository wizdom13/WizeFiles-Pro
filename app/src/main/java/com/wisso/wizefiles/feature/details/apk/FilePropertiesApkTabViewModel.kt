// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.details.apk

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import java.nio.file.Path
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.util.Stateful

class FilePropertiesApkTabViewModel(path: Path) : ViewModel() {
    private val _apkInfoLiveData = ApkInfoLiveData(path.toAppPath())
    val apkInfoLiveData: LiveData<Stateful<ApkInfo>>
        get() = _apkInfoLiveData

    fun reload() {
        _apkInfoLiveData.loadValue()
    }

    override fun onCleared() {
        _apkInfoLiveData.close()
    }
}
