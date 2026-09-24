// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.files.extensions

import android.text.format.DateUtils
import java.time.Duration

fun Duration.format(): String = DateUtils.formatElapsedTime(seconds)
