package com.wisso.wizefiles.settings

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.wisso.wizefiles.core.files.mime.MimeType
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal object SettingsBackupViewIntent {
    private const val WZF_EXTENSION = ".wzf"

    fun findBackupUri(intent: Intent, resolveDisplayName: (Uri) -> String? = { null }): Uri? {
        if (intent.action != Intent.ACTION_VIEW) {
            return null
        }
        val uri = intent.data ?: return null
        return if (intent.type == MimeType.WIZEFILES_BACKUP.value ||
            isBackupUri(uri, resolveDisplayName)
        ) {
            uri
        } else {
            null
        }
    }

    fun resolveDisplayName(context: Context, uri: Uri): String? =
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)
                    } else {
                        null
                    }
                }
        } else {
            null
        }

    private fun isBackupUri(uri: Uri, resolveDisplayName: (Uri) -> String?): Boolean {
        val candidates = buildList {
            add(uri.lastPathSegment)
            add(uri.path)
            add(uri.toString())
            add(resolveDisplayName(uri))
        }
        return matchesBackupCandidates(candidates)
    }

    internal fun matchesBackupCandidates(candidates: List<String?>): Boolean =
        candidates
            .filterNotNull()
            .any { candidate ->
                val decoded = decodeVariants(candidate)
                if (decoded.any(::containsNestedSchemePayload)) {
                    false
                } else {
                    decoded.any(::hasBackupExtension)
                }
            }

    internal fun isBackupCandidateValue(value: String): Boolean {
        val decoded = decodeVariants(value)
        if (decoded.any(::containsNestedSchemePayload)) {
            return false
        }
        return decoded.any(::hasBackupExtension)
    }

    private fun decodeVariants(value: String): List<String> {
        val variants = mutableListOf(value)
        var current = value
        repeat(3) {
            val decoded = runCatching {
                URLDecoder.decode(current, StandardCharsets.UTF_8.name())
            }.getOrDefault(current)
            if (decoded == current) {
                return@repeat
            }
            variants += decoded
            current = decoded
        }
        return variants
    }

    private fun containsNestedSchemePayload(value: String): Boolean {
        val normalized = value.lowercase()
        return normalized.contains("file:") || normalized.contains("content:") ||
            normalized.contains("http:") || normalized.contains("https:") ||
            normalized.contains("javascript:")
    }

    private fun hasBackupExtension(value: String): Boolean {

        val normalized = value.substringBefore('?').substringBefore('#')
        return normalized.endsWith(WZF_EXTENSION, ignoreCase = true)
    }
}
