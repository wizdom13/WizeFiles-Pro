// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import java.time.Instant
import java.nio.file.attribute.FileTime
import kotlin.reflect.KClass

val KClass<FileTime>.EPOCH: FileTime
    get() = FileTime.from(Instant.EPOCH)
