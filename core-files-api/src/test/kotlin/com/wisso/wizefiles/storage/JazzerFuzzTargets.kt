package com.wisso.wizefiles.storage

/** Coverage-guided Jazzer entry point; classified frame failures are expected parser outcomes. */
object VaultFrameFuzzTarget {
    @JvmStatic fun fuzzerTestOneInput(input: ByteArray) {
        try {
            val decoded = VaultPayloadFrame.decode(input)
            check(decoded.size <= VaultPayloadFrame.MAX_PAYLOAD_BYTES)
            check(VaultPayloadFrame.decode(VaultPayloadFrame.encode(decoded)).contentEquals(decoded))
        } catch (_: FrameValidationException) {
            // Classified rejection.
        }
    }
}

/** Coverage-guided Jazzer entry point for archive-entry normalization. */
object ArchivePathFuzzTarget {
    @JvmStatic fun fuzzerTestOneInput(input: ByteArray) {
        val candidate = input.toString(Charsets.UTF_8)
        try {
            val accepted = SecureRelativePath.validate(candidate)
            check(accepted.isNotEmpty() && !accepted.startsWith('/'))
            check(".." !in accepted.split('/'))
        } catch (_: IllegalArgumentException) {
            // Classified rejection.
        }
    }
}
