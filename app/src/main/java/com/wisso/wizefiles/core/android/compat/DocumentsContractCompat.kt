// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.android.compat

import android.Manifest
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import com.wisso.wizefiles.core.app.packageManager

object DocumentsContractCompat {
    const val EXTRA_INITIAL_URI = "android.provider.extra.INITIAL_URI"
    const val EXTRA_SHOW_ADVANCED = "android.provider.extra.SHOW_ADVANCED"
    const val EXTERNAL_STORAGE_PROVIDER_AUTHORITY = "com.android.externalstorage.documents"
    const val EXTERNAL_STORAGE_PRIMARY_EMULATED_ROOT_ID = "primary"

    fun getDocumentsUiPackage(): String? {
        val candidates = packageManager.getPackagesHoldingPermissions(
            arrayOf(Manifest.permission.MANAGE_DOCUMENTS),
            0
        )
        return candidates.firstOrNull { info ->
            info.packageName.endsWith(DOCUMENTS_UI_SUFFIX)
        }?.packageName ?: candidates.firstOrNull()?.packageName
    }

    fun isDocumentUri(uri: Uri): Boolean {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
        return uri.pathSegments.matches(DOCUMENT_PATH) ||
            uri.pathSegments.matches(TREE_DOCUMENT_PATH)
    }

    fun isTreeUri(uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return DocumentsContract.isTreeUri(uri)
        }
        val segments = uri.pathSegments
        return segments.size >= 2 && segments.firstOrNull() == TREE
    }

    fun isChildDocumentsUri(uri: Uri): Boolean =
        uri.pathSegments.matches(DOCUMENT_CHILDREN_PATH) ||
            uri.pathSegments.matches(TREE_DOCUMENT_CHILDREN_PATH)

    private fun List<String>.matches(pattern: List<String?>): Boolean =
        size == pattern.size && indices.all { index ->
            pattern[index] == null || this[index] == pattern[index]
        }

    private const val DOCUMENTS_UI_SUFFIX = ".documentsui"
    private const val DOCUMENT = "document"
    private const val CHILDREN = "children"
    private const val TREE = "tree"
    private val DOCUMENT_PATH = listOf(DOCUMENT, null)
    private val TREE_DOCUMENT_PATH = listOf(TREE, null, DOCUMENT, null)
    private val DOCUMENT_CHILDREN_PATH = listOf(DOCUMENT, null, CHILDREN)
    private val TREE_DOCUMENT_CHILDREN_PATH = listOf(TREE, null, DOCUMENT, null, CHILDREN)
}
