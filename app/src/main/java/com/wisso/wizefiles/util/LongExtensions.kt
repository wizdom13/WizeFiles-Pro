package com.wisso.wizefiles.util

fun Long.hasBits(bits: Long): Boolean = this and bits == bits

infix fun Long.andInv(other: Long): Long = this and other.inv()
