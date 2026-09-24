package com.wisso.wizefiles.viewer.text

import com.wisso.wizefiles.core.files.mime.MimeType
import java.util.Locale

/**
 * Mirrors the MIME contract exposed by [TextEditorActivity] for direct in-app edit actions.
 */
val MimeType.isTextEditorSupported: Boolean
    get() {
        if (type.equals("text", ignoreCase = true)) return true
        return value.substringBefore(';').lowercase(Locale.ROOT) in supportedApplicationMimeTypes
    }

private val supportedApplicationMimeTypes = setOf(
    "application/ecmascript",
    "application/javascript",
    "application/json",
    "application/typescript",
    "application/x-sh",
    "application/x-shellscript",
    "application/xml",
    "application/yaml"
)
