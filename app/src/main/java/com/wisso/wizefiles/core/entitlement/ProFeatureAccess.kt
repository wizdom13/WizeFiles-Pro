// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.entitlement

/**
 * Compatibility shim for call sites that used to enforce WizeFiles Pro.
 *
 * Since 1.0.0 every implemented feature is included. New code should not add commercial feature
 * gates; existing callers may keep using this object until they are naturally refactored.
 */
object ProFeatureAccess {
    fun isAllowed(@Suppress("UNUSED_PARAMETER") feature: ProFeature): Boolean = true
    fun require(@Suppress("UNUSED_PARAMETER") feature: ProFeature) = Unit
}
