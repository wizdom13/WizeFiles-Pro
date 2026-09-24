package com.wisso.wizefiles.util

import androidx.lifecycle.LiveData

abstract class StatefulLiveData<T : Any> : LiveData<Stateful<T>>(Loading(null)) {

    val isReady: Boolean
        get() {
            val state = valueCompat
            return state is Loading && state.value == null
        }

    fun reset() {
        val state = valueCompat
        check(state !is Loading || state.value == null) {
            "Cannot reset while a value is being refreshed"
        }
        value = Loading(null)
    }
}
