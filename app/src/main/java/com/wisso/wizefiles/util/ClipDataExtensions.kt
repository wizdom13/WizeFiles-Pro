// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

import android.content.ClipData
import kotlin.reflect.KClass

fun ClipData.firstOrNull(): ClipData.Item? = if (itemCount > 0) getItemAt(0) else null

fun KClass<ClipData>.create(
    label: CharSequence?,
    mimeTypes: List<String>,
    items: List<ClipData.Item>
): ClipData =
    ClipData(label, mimeTypes.toTypedArray(), items[0])
        .apply { items.asSequence().drop(1).forEach { addItem(it) } }
