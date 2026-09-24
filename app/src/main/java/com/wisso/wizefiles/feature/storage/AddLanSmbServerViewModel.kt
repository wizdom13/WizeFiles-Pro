package com.wisso.wizefiles.storage

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.wisso.wizefiles.util.Stateful

class AddLanSmbServerViewModel : ViewModel() {
    private val source = LanSmbServerListLiveData()
    val lanSmbServerListLiveData: LiveData<Stateful<List<LanSmbServer>>>
        get() = source

    fun reload() = source.loadValue()

    override fun onCleared() {
        source.close()
        super.onCleared()
    }
}
