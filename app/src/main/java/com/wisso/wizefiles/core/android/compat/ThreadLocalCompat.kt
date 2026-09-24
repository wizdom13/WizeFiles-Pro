package com.wisso.wizefiles.core.android.compat

import kotlin.reflect.KClass

fun <T> KClass<ThreadLocal<*>>.withInitial(factory: () -> T): ThreadLocal<T> =
    object : ThreadLocal<T>() {
        override fun initialValue(): T = factory()
    }
