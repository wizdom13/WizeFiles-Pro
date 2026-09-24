package com.wisso.wizefiles.feature.crashreport

object CrashReportSanitizer {
    private val keyValuePattern = Regex(
        """(?i)\b(password|passwd|token|secret|api[_-]?key|authorization|cookie|set-cookie|private[_-]?key(?:[_-]?password)?|passphrase)\b\s*[:=]\s*([^\s,;]+)"""
    )
    private val authorizationPattern =
        Regex("""(?i)(authorization\s*[:=]\s*)(bearer|basic)\s+([^\s,;]+)""")
    private val secretQueryPattern = Regex(
        """([?&](?:password|passwd|token|secret|apikey|api_key|access_token|refresh_token|auth|signature)=)([^&\s]+)""",
        RegexOption.IGNORE_CASE
    )
    private val uriCredentialsPattern =
        Regex("""(?i)\b([a-z][a-z0-9+.-]*://)([^\s/@:]+)(?::([^\s/@]*))?@""")
    private val privateKeyBlockPattern =
        Regex("""-----BEGIN [^-]*PRIVATE KEY-----[\s\S]*?-----END [^-]*PRIVATE KEY-----""")
    private val uriPattern =
        Regex("""(?i)\b(?:content|file|smb|sftp|ftp|ftps|webdav|https?)://[^\s"'<>]+""")
    private val pathPattern =
        Regex("""(?i)(?:/storage/|/sdcard/|/mnt/|/data/user/\d+/|/data/data/)[^\s"'<>]+""")
    private val emailPattern =
        Regex("""\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""", RegexOption.IGNORE_CASE)

    fun sanitize(value: String): String =
        value
            .replace(privateKeyBlockPattern, "<redacted-private-key>")
            .replace(authorizationPattern) { match ->
                "${match.groupValues[1]}${match.groupValues[2]} <redacted>"
            }
            .replace(keyValuePattern) { match ->
                "${match.groupValues[1]}=<redacted>"
            }
            .replace(secretQueryPattern) { match ->
                "${match.groupValues[1]}<redacted>"
            }
            .replace(uriCredentialsPattern) { match ->
                "${match.groupValues[1]}<redacted>@"
            }
            .replace(uriPattern, "<redacted-uri>")
            .replace(pathPattern, "<redacted-path>")
            .replace(emailPattern, "<redacted-email>")
}
