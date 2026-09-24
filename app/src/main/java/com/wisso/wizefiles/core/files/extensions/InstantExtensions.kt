// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.files.extensions

import android.content.Context
import android.text.format.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/* @see com.android.documentsui.base.Shared#formatTime(Context, long) */
fun Instant.formatShort(context: Context): String {
    val time = toEpochMilli()
    val zone = ZoneId.systemDefault()
    val then = atZone(zone).toLocalDate()
    val now = LocalDate.now(zone)
    val flags = DateUtils.FORMAT_NO_NOON or DateUtils.FORMAT_NO_MIDNIGHT or
        DateUtils.FORMAT_ABBREV_ALL or when {
            then.year != now.year -> DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_DATE
            then != now -> DateUtils.FORMAT_SHOW_DATE
            else -> DateUtils.FORMAT_SHOW_TIME
        }
    return DateUtils.formatDateTime(context, time, flags)
}

fun Instant.formatLong(): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
        .format(this)
