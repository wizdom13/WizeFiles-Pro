package com.wisso.wizefiles.feature.details

import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.core.files.model.loadFileItem
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.util.Failure
import com.wisso.wizefiles.util.Loading
import com.wisso.wizefiles.util.Stateful
import com.wisso.wizefiles.util.Success
import com.wisso.wizefiles.util.valueCompat
import com.wisso.wizefiles.util.backgroundExecutor

class FileLiveData private constructor(
    path: AppPath,
    file: FileItem?
) : PathObserverLiveData<Stateful<FileItem>>(path) {
    constructor(path: AppPath) : this(path, null)

    constructor(file: FileItem) : this(file.path, file)

    init {
        if (file != null) {
            value = Success(file)
        } else {
            loadValue()
        }
        observe()
    }

    override fun loadValue() {
        value = Loading(value?.value)
        backgroundExecutor.execute {
            val value = try {
                val file = path.loadFileItem()
                Success(file)
            } catch (e: Exception) {
                Failure(valueCompat.value, e)
            }
            postValue(value)
        }
    }
}
