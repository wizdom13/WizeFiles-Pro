// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.imageloader.coil

import coil.request.ImageRequest
import coil.transition.CrossfadeTransition

fun ImageRequest.Builder.fadeIn(durationMillis: Int): ImageRequest.Builder =
    apply {
        placeholder(android.R.color.transparent)
        transitionFactory(CrossfadeTransition.Factory(durationMillis, true))
    }
