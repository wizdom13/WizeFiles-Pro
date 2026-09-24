// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

import android.util.TypedValue
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass

inline fun <T> KClass<TypedValue>.useTemp(block: (TypedValue) -> T): T {
    val temp = TypedValue::class.obtainTemp()
    return try {
        block(temp)
    } finally {
        temp.releaseTemp()
    }
}

private val tempTypedValue = AtomicReference(TypedValue())

fun KClass<TypedValue>.obtainTemp(): TypedValue = tempTypedValue.getAndSet(null) ?: TypedValue()

fun TypedValue.releaseTemp() {
    tempTypedValue.compareAndSet(null, this)
}
