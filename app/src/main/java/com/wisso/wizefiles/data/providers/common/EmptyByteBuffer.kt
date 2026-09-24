package com.wisso.wizefiles.provider.common

import java.nio.ByteBuffer
import kotlin.reflect.KClass

val KClass<ByteBuffer>.EMPTY: ByteBuffer
    get() = ByteBuffer.allocate(0)
