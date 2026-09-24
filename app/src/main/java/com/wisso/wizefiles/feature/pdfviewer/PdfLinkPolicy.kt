package com.wisso.wizefiles.feature.pdfviewer

/** Restricts PDF links to schemes that cannot address local app or provider data. */
object PdfLinkPolicy {
    fun allowsScheme(scheme: String?): Boolean =
        ALLOWED_SCHEMES.any { it.equals(scheme, ignoreCase = true) }

    private val ALLOWED_SCHEMES = setOf("http", "https", "mailto")
}
