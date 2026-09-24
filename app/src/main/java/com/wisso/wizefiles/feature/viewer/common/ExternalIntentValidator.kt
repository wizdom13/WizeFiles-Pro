package com.wisso.wizefiles.viewer.common

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.wisso.wizefiles.BuildConfig
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ExternalIntentValidator {
    private const val MAX_TEXT_BYTES = 1024 * 1024L
    private const val MAX_IMAGE_EDIT_BYTES = 50L * 1024 * 1024
    private const val MAX_VIDEO_EDIT_BYTES = 1024L * 1024 * 1024
    private val blockedNestedSchemes = listOf("file:", "content:", "http:", "https:", "javascript:")

    enum class InputKind { IMAGE_VIEW, VIDEO_VIEW, TEXT_VIEW, IMAGE_EDIT, VIDEO_EDIT, SETTINGS_IMPORT }

    data class ValidationResult(val uri: Uri, val sizeBytes: Long?)

    suspend fun validate(context: Context, uri: Uri?, kind: InputKind): ValidationResult? {
        val safeUri = uri?.takeIf(::isTrustedExternalUri) ?: return null
        val size = withContext(Dispatchers.IO) {
            try {
                querySize(context, safeUri)
            } catch (exception: Exception) {
                if (exception is CancellationException) {
                    throw exception
                }
                null
            }
        }
        val maxSize = when (kind) {
            InputKind.TEXT_VIEW -> MAX_TEXT_BYTES
            InputKind.IMAGE_EDIT -> MAX_IMAGE_EDIT_BYTES
            InputKind.VIDEO_EDIT -> MAX_VIDEO_EDIT_BYTES
            InputKind.IMAGE_VIEW, InputKind.VIDEO_VIEW, InputKind.SETTINGS_IMPORT -> Long.MAX_VALUE
        }
        if (size != null && size > maxSize) {
            return null
        }
        return ValidationResult(safeUri, size)
    }

    fun isSupportedLocalExternalUri(uri: Uri): Boolean =
        isSupportedLocalExternalUriString(uri.toString(), uri.scheme)

    internal fun isSupportedLocalExternalUriString(
        serialized: String,
        explicitScheme: String? = null
    ): Boolean {
        val scheme = explicitScheme?.lowercase(Locale.ROOT) ?: run {
            val index = serialized.indexOf(":")
            if (index <= 0) return false
            serialized.substring(0, index).lowercase(Locale.ROOT)
        }
        return (scheme == ContentResolver.SCHEME_CONTENT || scheme == ContentResolver.SCHEME_FILE) &&
            !serialized.contains('\u0000')
    }

    fun isTrustedExternalUri(uri: Uri): Boolean = isTrustedExternalUriString(uri.toString(), uri.scheme)

    internal fun isTrustedExternalUriString(serialized: String, explicitScheme: String? = null): Boolean {
        if (!isSupportedLocalExternalUriString(serialized, explicitScheme)) {
            return false
        }
        val scheme = explicitScheme?.lowercase(Locale.ROOT) ?: serialized.substringBefore(':').lowercase(Locale.ROOT)
        val decodedOnce = runCatching {
            URLDecoder.decode(serialized, StandardCharsets.UTF_8.name())
        }.getOrDefault(serialized)
        if (decodedOnce != serialized) {
            val normalized = decodedOnce.lowercase(Locale.ROOT)
            val parsed = runCatching { Uri.parse(serialized) }.getOrNull()
            val isOwnFileProviderWrapper = scheme == ContentResolver.SCHEME_CONTENT &&
                parsed?.authority == BuildConfig.FILE_PROVIDER_AUTHORITY
            if (!isOwnFileProviderWrapper && blockedNestedSchemes.any { it in normalized }) {
                return false
            }
        }
        return true
    }

    private fun querySize(context: Context, uri: Uri): Long? {
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getLong(0).takeIf { it >= 0L }
                }
        } else {
            uri.path?.let { java.io.File(it).takeIf { file -> file.exists() }?.length() }
        }
    }
}
