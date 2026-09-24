// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

internal enum class RcloneRegularSetup {
    OAUTH,
    MEGA_CREDENTIALS,
    SIMPLE,
    GENERATED
}

internal fun regularSetupFor(backendType: String?): RcloneRegularSetup =
    when (backendType) {
        "drive", "onedrive", "dropbox", "box", "pcloud" -> RcloneRegularSetup.OAUTH
        "mega" -> RcloneRegularSetup.MEGA_CREDENTIALS
        "webdav", "s3" -> RcloneRegularSetup.SIMPLE
        else -> RcloneRegularSetup.GENERATED
    }

internal fun brandedProviderRank(backendType: String): Int =
    BRANDED_PROVIDER_ORDER.indexOf(backendType).takeIf { it >= 0 } ?: Int.MAX_VALUE

internal const val OTHER_CLOUD_PROVIDERS_KEY = "__other_cloud_providers__"

internal fun cloudProviderMenuKeys(
    allProviderKeys: List<String>,
    expanded: Boolean
): List<String> =
    if (expanded) {
        allProviderKeys
    } else {
        allProviderKeys.filter { it in FEATURED_PROVIDER_TYPES } +
            OTHER_CLOUD_PROVIDERS_KEY
    }

internal fun defaultCloudAccountName(
    backendType: String?,
    providerTitle: String
): String? =
    when (backendType) {
        "drive" -> "Drive"
        "onedrive" -> "OneDrive"
        "dropbox" -> "Dropbox"
        "box" -> "Box"
        "pcloud" -> "pCloud"
        "mega" -> "MEGA"
        "webdav" -> "WebDAV"
        "s3" -> "S3"
        null -> null
        else -> providerTitle
    }

internal fun automaticRegularConfigAnswer(
    backendType: String,
    optionName: String
): String? {
    if (regularSetupFor(backendType) != RcloneRegularSetup.OAUTH) {
        return null
    }
    return when {
        optionName == "config_refresh_token" || optionName == "config_is_local" -> "true"
        backendType == "drive" && optionName == "config_change_team_drive" -> "false"
        else -> null
    }
}

private val FEATURED_PROVIDER_TYPES = setOf(
    "drive",
    "onedrive",
    "dropbox",
    "box",
    "pcloud",
    "mega"
)

private val BRANDED_PROVIDER_ORDER = listOf(
    "drive",
    "onedrive",
    "dropbox",
    "box",
    "pcloud",
    "mega",
    "webdav",
    "s3"
)
