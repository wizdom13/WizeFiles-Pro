package com.wisso.wizefiles.core.files.extensions

import android.text.format.DateUtils
import java.time.Duration

fun Duration.format(): String = DateUtils.formatElapsedTime(seconds)
