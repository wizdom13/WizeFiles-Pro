package com.wisso.wizefiles.feature.details.checksum

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import java.nio.file.Path
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.util.Stateful

class FilePropertiesChecksumTabViewModel(path: Path) : ViewModel() {
    private val _checksumInfoLiveData = ChecksumInfoLiveData(path.toAppPath())
    val checksumInfoLiveData: LiveData<Stateful<ChecksumInfo>>
        get() = _checksumInfoLiveData

    fun reload() {
        _checksumInfoLiveData.loadValue()
    }

    override fun onCleared() {
        _checksumInfoLiveData.close()
    }
}
