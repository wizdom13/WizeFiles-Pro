package com.wisso.wizefiles.util

fun Any.hash(vararg values: Any?): Int = values.contentDeepHashCode()
