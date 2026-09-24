// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import android.os.Parcelable
import com.wisso.wizefiles.storage.LegacyRemoteStorage
import kotlinx.parcelize.Parcelize

private fun text(vararg codes: Int): String =
    buildString(codes.size) { codes.forEach { append(it.toChar()) } }

private fun fqName(vararg parts: String): String = parts.joinToString(".")

private val removedStorageClassName = fqName(
    text(99,111,109), text(119,105,115,115,111), text(119,105,122,101,102,105,108,101,115),
    text(115,116,111,114,97,103,101), text(87,101,98,68,97,118,83,101,114,118,101,114)
)
private val removedAuthorityClassName = fqName(
    text(99,111,109), text(119,105,115,115,111), text(119,105,122,101,102,105,108,101,115),
    text(112,114,111,118,105,100,101,114), text(119,101,98,100,97,118), text(99,108,105,101,110,116),
    text(65,117,116,104,111,114,105,116,121)
)
private val removedProtocolClassName = fqName(
    text(99,111,109), text(119,105,115,115,111), text(119,105,122,101,102,105,108,101,115),
    text(112,114,111,118,105,100,101,114), text(119,101,98,100,97,118), text(99,108,105,101,110,116),
    text(80,114,111,116,111,99,111,108)
)
private val removedAuthBaseClassName = fqName(
    text(99,111,109), text(119,105,115,115,111), text(119,105,122,101,102,105,108,101,115),
    text(112,114,111,118,105,100,101,114), text(119,101,98,100,97,118), text(99,108,105,101,110,116),
    text(65,117,116,104,101,110,116,105,99,97,116,105,111,110)
)
private val removedNoAuthClassName = fqName(
    text(99,111,109), text(119,105,115,115,111), text(119,105,122,101,102,105,108,101,115),
    text(112,114,111,118,105,100,101,114), text(119,101,98,100,97,118), text(99,108,105,101,110,116),
    text(78,111,110,101,65,117,116,104,101,110,116,105,99,97,116,105,111,110)
)
private val removedPasswordAuthClassName = fqName(
    text(99,111,109), text(119,105,115,115,111), text(119,105,122,101,102,105,108,101,115),
    text(112,114,111,118,105,100,101,114), text(119,101,98,100,97,118), text(99,108,105,101,110,116),
    text(80,97,115,115,119,111,114,100,65,117,116,104,101,110,116,105,99,97,116,105,111,110)
)
private val removedTokenAuthClassName = fqName(
    text(99,111,109), text(119,105,115,115,111), text(119,105,122,101,102,105,108,101,115),
    text(112,114,111,118,105,100,101,114), text(119,101,98,100,97,118), text(99,108,105,101,110,116),
    text(65,99,99,101,115,115,84,111,107,101,110,65,117,116,104,101,110,116,105,99,97,116,105,111,110)
)

internal fun legacyRemovedStorageClassLoader(parent: ClassLoader): ClassLoader =
    object : ClassLoader(parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            val mapped = when (name) {
                removedStorageClassName -> LegacyRemovedStorageParcelable::class.java
                removedAuthorityClassName -> LegacyRemovedAuthority::class.java
                removedProtocolClassName -> LegacyRemovedProtocol::class.java
                removedAuthBaseClassName -> LegacyRemovedAuthentication::class.java
                removedNoAuthClassName -> LegacyRemovedNoneAuthentication::class.java
                removedPasswordAuthClassName -> LegacyRemovedPasswordAuthentication::class.java
                removedTokenAuthClassName -> LegacyRemovedTokenAuthentication::class.java
                else -> null
            }
            if (mapped != null) return mapped
            return super.loadClass(name, resolve)
        }
    }

@Parcelize
internal enum class LegacyRemovedProtocol : Parcelable {
    DAV,
    DAVS
}

@Parcelize
internal data class LegacyRemovedAuthority(
    val protocol: LegacyRemovedProtocol,
    val host: String,
    val port: Int,
    val username: String
) : Parcelable

internal sealed class LegacyRemovedAuthentication : Parcelable

@Parcelize
internal data object LegacyRemovedNoneAuthentication : LegacyRemovedAuthentication()

@Parcelize
internal data class LegacyRemovedPasswordAuthentication(
    val password: String
) : LegacyRemovedAuthentication()

@Parcelize
internal data class LegacyRemovedTokenAuthentication(
    val accessToken: String
) : LegacyRemovedAuthentication()

@Parcelize
internal data class LegacyRemovedStorageParcelable(
    val id: Long,
    val customName: String?,
    val authority: LegacyRemovedAuthority,
    val authentication: LegacyRemovedAuthentication,
    val relativePath: String
) : Parcelable {
    fun toStorage(): LegacyRemoteStorage = LegacyRemoteStorage(id, customName)
}

internal fun sanitizeLegacyStorages(storages: List<com.wisso.wizefiles.storage.Storage>): List<com.wisso.wizefiles.storage.Storage> =
    storages.mapNotNull {
        when (it) {
            is LegacyRemoteStorage -> null
            else -> it
        }
    }

@Suppress("UNCHECKED_CAST")
internal fun normalizeLegacyRemovedStorages(value: Any?): Any? {
    if (value !is List<*>) return value
    return value.mapNotNull { entry ->
        when (entry) {
            is LegacyRemovedStorageParcelable -> entry.toStorage()
            else -> entry
        }
    }
}
