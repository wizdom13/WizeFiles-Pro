package com.wisso.wizefiles.vault

object VaultPasswordValidator {
    fun validate(password: String, confirmation: String): String? {
        if (password.isEmpty()) {
            return "empty"
        }
        if (password != confirmation) {
            return "mismatch"
        }
        return null
    }
}
