package com.wisso.wizefiles.storage

import android.content.Context
import android.content.Intent
import com.wisso.wizefiles.core.files.uri.DocumentTreeUri
import com.wisso.wizefiles.core.files.uri.displayName
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.util.valueCompat

private const val DOCUMENTS_PROVIDER_INTERFACE = "android.content.action.DOCUMENTS_PROVIDER"

data class InstalledSafProvider(
    val authority: String,
    val packageName: String,
    val displayName: String
) {
    val isGoogleDriveLike: Boolean
        get() = displayName.contains("google drive", ignoreCase = true)
            || displayName.equals("drive", ignoreCase = true)
            || packageName.contains("docs", ignoreCase = true)
            || authority.contains("drive", ignoreCase = true)

    val isDropboxLike: Boolean
        get() = displayName.contains("dropbox", ignoreCase = true)
            || packageName.contains("dropbox", ignoreCase = true)
            || authority.contains("dropbox", ignoreCase = true)
}

object InstalledSafProviders {
    private val EXCLUDED_AUTHORITIES = setOf(
        "com.android.externalstorage.documents",
        "com.android.providers.downloads.documents",
        "com.android.providers.media.documents",
        "com.android.mtp.documents",
        "com.android.shell.documents"
    )
    private val EXCLUDED_PACKAGE_SUBSTRINGS = listOf(
        ".documentsui",
        ".permissioncontroller"
    )
    private val EXCLUDED_LABEL_SUBSTRINGS = listOf(
        "system tracing",
        "files",
        "debug"
    )

    @Suppress("DEPRECATION")
    fun queryProviders(context: Context): List<InstalledSafProvider> {
        val packageManager = context.packageManager
        val intent = Intent(DOCUMENTS_PROVIDER_INTERFACE)
        val providerInfos = packageManager.queryIntentContentProviders(intent, 0)
        return providerInfos
            .flatMap { resolveInfo ->
                val providerInfo = resolveInfo.providerInfo ?: return@flatMap emptyList()
                val packageName = providerInfo.packageName ?: return@flatMap emptyList()
                val providerLabel = providerInfo.loadLabel(packageManager)
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                    .ifEmpty {
                        providerInfo.applicationInfo.loadLabel(packageManager)
                            ?.toString()
                            ?.trim()
                            .orEmpty()
                    }
                    .ifEmpty { packageName }
                providerInfo.authority
                    ?.split(';')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.mapNotNull { authority ->
                        if (!isSelectableAuthority(context, authority, packageName, providerLabel)) {
                            null
                        } else {
                            InstalledSafProvider(
                                authority = authority,
                                packageName = packageName,
                                displayName = chooseDisplayName(providerLabel)
                            )
                        }
                    }
                    .orEmpty()
            }
            .distinctBy { it.authority }
            .sortedWith(
                compareBy<InstalledSafProvider> {
                    when {
                        it.isGoogleDriveLike -> 0
                        it.isDropboxLike -> 1
                        else -> 2
                    }
                }.thenBy { it.displayName.lowercase() }
            )
    }


    fun findProvider(context: Context, authority: String?): InstalledSafProvider? {
        if (authority == null) {
            return null
        }
        return queryProviders(context).firstOrNull { it.authority == authority }
    }

    fun resolveInitialUri(context: Context, providerAuthority: String?): android.net.Uri? {
        if (providerAuthority == null) {
            return null
        }
        return DocumentTreeUri.persistedUris.firstOrNull { it.value.authority == providerAuthority }?.value
    }

    fun buildSuggestedCustomName(
        context: Context,
        treeUri: DocumentTreeUri,
        fallbackProviderLabel: String? = null
    ): String? {
        val providerLabel = findProvider(context, treeUri.value.authority)?.displayName
            ?: fallbackProviderLabel
            ?: return null
        val directoryName = treeUri.displayName?.trim().orEmpty()
        return if (directoryName.isNotEmpty() && !directoryName.equals(providerLabel, ignoreCase = true)) {
            context.getString(
                com.wisso.wizefiles.R.string.storage_cloud_document_tree_name_format,
                providerLabel,
                directoryName
            )
        } else {
            providerLabel
        }
    }

    fun syncPersistedCloudStorages(context: Context) {
        val storages = Settings.STORAGES.valueCompat.toMutableList()
        val existingTreesByUri = storages
            .filterIsInstance<DocumentTree>()
            .associateBy { it.uri.value.toString() }
        var changed = false
        for (treeUri in DocumentTreeUri.persistedUris) {
            if (findProvider(context, treeUri.value.authority) == null) {
                continue
            }
            if (existingTreesByUri.containsKey(treeUri.value.toString())) {
                continue
            }
            storages += DocumentTree(
                id = null,
                customName = buildSuggestedCustomName(context, treeUri),
                uri = treeUri
            )
            changed = true
        }
        if (changed) {
            Settings.STORAGES.putValue(storages)
        }
    }

    private fun chooseDisplayName(providerLabel: String): String {
        return if (providerLabel.equals("Drive", ignoreCase = true)) {
            "Google Drive"
        } else {
            providerLabel
        }
    }

    private fun isSelectableAuthority(
        context: Context,
        authority: String,
        packageName: String,
        providerLabel: String
    ): Boolean {
        if (authority in EXCLUDED_AUTHORITIES) {
            return false
        }
        if (packageName == context.packageName) {
            return false
        }
        if (EXCLUDED_PACKAGE_SUBSTRINGS.any { packageName.contains(it, ignoreCase = true) }) {
            return false
        }
        if (EXCLUDED_LABEL_SUBSTRINGS.any { providerLabel.contains(it, ignoreCase = true) }) {
            return false
        }
        return true
    }
}
