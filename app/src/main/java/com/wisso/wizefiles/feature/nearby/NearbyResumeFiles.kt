// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Discard only the unacknowledged tail of an already path-validated staging file. */
internal fun prepareNearbyResumeFile(temporary: Path, acknowledgedOffset: Long) {
    require(acknowledgedOffset >= 0)
    val existing = if (Files.exists(temporary)) Files.size(temporary) else 0L
    require(existing >= acknowledgedOffset) { "Temporary file is shorter than the resume checkpoint" }
    if (existing > acknowledgedOffset) {
        Files.newByteChannel(temporary, StandardOpenOption.WRITE).use { it.truncate(acknowledgedOffset) }
    }
}
