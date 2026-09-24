// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.security

import android.os.Parcel
import kotlinx.parcelize.Parceler
import com.wisso.wizefiles.core.app.secretStore

object SecretStringParceler : Parceler<String> {
    override fun create(parcel: Parcel): String {
        val stored = parcel.readString()
        return stored.resolveSecret().orEmpty()
    }

    override fun String.write(parcel: Parcel, flags: Int) {
        parcel.writeString(storeSecret(this))
    }
}

object NullableSecretStringParceler : Parceler<String?> {
    override fun create(parcel: Parcel): String? {
        val stored = parcel.readString()
        return stored.resolveSecret()
    }

    override fun String?.write(parcel: Parcel, flags: Int) {
        parcel.writeString(this?.let { storeSecret(it) })
    }
}

private fun String?.resolveSecret(): String? {
    if (this == null) {
        return null
    }
    return SecretMigration.resolveStoredSecret(this, secretStore)
}

private fun storeSecret(value: String): String {
    return SecretMigration.storeSecret(value, secretStore)
}

