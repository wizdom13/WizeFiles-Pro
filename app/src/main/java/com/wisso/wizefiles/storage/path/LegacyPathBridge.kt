// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage.path

import java.io.File

fun AppPath.asLocalAppPathOrNull(): LocalAppPath? = this as? LocalAppPath

fun AppPath.toLocalFileOrNull(): File? = asLocalAppPathOrNull()?.file
