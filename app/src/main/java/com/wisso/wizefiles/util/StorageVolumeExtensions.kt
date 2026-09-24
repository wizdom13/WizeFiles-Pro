// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

import android.os.storage.StorageVolume
import com.wisso.wizefiles.core.android.compat.pathFileCompat

val StorageVolume.isMounted: Boolean
    get() = pathFileCompat != null
