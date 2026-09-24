package com.wisso.wizefiles.feature.details.video

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import java.nio.file.Path
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.util.Stateful

class FilePropertiesVideoTabViewModel(path: Path) : ViewModel() {
    private val _videoInfoLiveData = VideoInfoLiveData(path.toAppPath())
    val videoInfoLiveData: LiveData<Stateful<VideoInfo>>
        get() = _videoInfoLiveData

    fun reload() {
        _videoInfoLiveData.loadValue()
    }

    override fun onCleared() {
        _videoInfoLiveData.close()
    }
}
