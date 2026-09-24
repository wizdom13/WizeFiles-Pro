package com.wisso.wizefiles.util

import androidx.lifecycle.LiveData
import com.wisso.wizefiles.settings.SettingLiveData

@Suppress("UNCHECKED_CAST")
val <T> LiveData<T>.valueCompat: T
    get() = when (this) {
        is SettingLiveData<*> -> currentValueCompat() as T
        else -> value as T
    }
