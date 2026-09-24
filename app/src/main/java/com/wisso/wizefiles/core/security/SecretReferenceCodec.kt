package com.wisso.wizefiles.security

import java.util.UUID
import java.util.regex.Pattern

object SecretReferenceCodec {
    private const val PREFIX = "secret_ref:"
    private val REFERENCE_PATTERN: Pattern =
        Pattern.compile("${PREFIX}[0-9a-fA-F\\-]{36}")

    fun createReference(): String = "$PREFIX${UUID.randomUUID()}"

    fun isReference(value: String): Boolean = value.startsWith(PREFIX)

    fun findReferences(value: String): Set<String> {
        val matcher = REFERENCE_PATTERN.matcher(value)
        val references = linkedSetOf<String>()
        while (matcher.find()) {
            references += matcher.group()
        }
        return references
    }
}
