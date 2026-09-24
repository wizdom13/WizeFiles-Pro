package com.wisso.wizefiles.feature.internalviewer

import java.util.Locale

/** Formats Android's native Typeface loader can consume without conversion. */
object FontOpenPolicy {
    private val extensions = setOf("ttf", "otf", "ttc")
    private val mimeTypes = setOf(
        "font/ttf",
        "font/otf",
        "font/collection",
        "font/sfnt",
        "application/font-sfnt",
        "application/x-font-ttf",
        "application/x-font-truetype",
        "application/x-font-otf",
        "application/x-font-opentype",
        "application/x-font-ttc"
    )

    fun supports(fileName: String?, mimeType: String?): Boolean =
        fileName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in extensions ||
            mimeType?.lowercase(Locale.ROOT) in mimeTypes
}
