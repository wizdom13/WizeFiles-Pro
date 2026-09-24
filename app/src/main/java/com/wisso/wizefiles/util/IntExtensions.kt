package com.wisso.wizefiles.util

fun Int.hasBits(bits: Int): Boolean = this and bits == bits

infix fun Int.andInv(other: Int): Int = this and other.inv()
