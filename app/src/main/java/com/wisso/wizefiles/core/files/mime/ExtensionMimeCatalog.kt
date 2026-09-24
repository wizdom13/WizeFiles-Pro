package com.wisso.wizefiles.core.files.mime

import java.util.Locale

/**
 * Resolves extensions that WizeFiles must recognize consistently across supported Android releases.
 * Android's registry remains the primary source for every extension that is not a product rule.
 */
internal object ExtensionMimeCatalog {

    fun resolve(
        rawExtension: String,
        platformLookup: (String) -> String?
    ): MimeType {
        val extension = rawExtension.trim().removePrefix(".").lowercase(Locale.ROOT)
        if (extension.isEmpty()) {
            return MimeType.GENERIC
        }

        val value = productType(extension)
            ?: platformLookup(extension)
            ?: portableFallback(extension)
        return value?.asMimeTypeOrNull() ?: MimeType.GENERIC
    }

    private fun productType(extension: String): String? = when (extension) {
        "wzf" -> MimeType.WIZEFILES_BACKUP.value
        "csv" -> "text/csv"
        "sh" -> "application/x-sh"
        "ts" -> "application/typescript"
        "py3", "py3x", "pyx", "wsgi" -> "text/x-python"
        "asm", "s" -> "text/x-asm"
        "cs" -> "text/x-csharp"
        "yml" -> "application/yaml"
        "mkd" -> "text/markdown"
        "conf", "ini", "list", "log", "prop", "properties", "rc" -> "text/plain"
        "p7b", "spc" -> "application/x-pkcs7-certificates"
        "azw" -> "application/vnd.amazon.ebook"
        "ibooks" -> "application/x-ibooks+zip"
        "msg" -> "application/vnd.ms-outlook"
        else -> null
    }

    /** Small safety net for formats WizeFiles opens directly on older Android releases. */
    private fun portableFallback(extension: String): String? = when (extension) {
        "7z" -> "application/x-7z-compressed"
        "bz" -> "application/x-bzip"
        "bz2" -> "application/x-bzip2"
        "gz" -> "application/gzip"
        "lzma" -> "application/x-lzma"
        "rar" -> "application/vnd.rar"
        "tar" -> "application/x-tar"
        "xz" -> "application/x-xz"
        "z" -> "application/x-compress"
        "zst" -> "application/zstd"
        "apk" -> "application/vnd.android.package-archive"
        "epub" -> "application/epub+zip"
        "mobi" -> "application/x-mobipocket-ebook"
        "md", "markdown" -> "text/markdown"
        "avif" -> "image/avif"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        "webp" -> "image/webp"
        else -> null
    }
}
