// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.security

interface SecretStore {
    fun getSecret(key: String): String?

    fun putSecret(key: String, value: String?)

    fun removeSecret(key: String)
}
