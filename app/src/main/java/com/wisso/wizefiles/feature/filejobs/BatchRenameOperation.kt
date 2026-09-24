// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import java.nio.file.Path

data class BatchRenameOperation(val path: Path, val newName: String)
