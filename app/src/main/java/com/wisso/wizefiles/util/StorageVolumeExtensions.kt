package com.wisso.wizefiles.util

import android.os.storage.StorageVolume
import com.wisso.wizefiles.core.android.compat.pathFileCompat

val StorageVolume.isMounted: Boolean
    get() = pathFileCompat != null
