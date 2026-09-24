// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import java.nio.ByteBuffer
import kotlin.reflect.KClass

val KClass<ByteBuffer>.EMPTY: ByteBuffer
    get() = ByteBuffer.allocate(0)
