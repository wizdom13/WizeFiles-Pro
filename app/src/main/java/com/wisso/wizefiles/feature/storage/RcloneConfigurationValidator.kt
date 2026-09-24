package com.wisso.wizefiles.storage

internal object RcloneConfigurationValidator {
    fun required(value: String): String? = value.trim().takeIf(String::isNotEmpty)

    fun advancedOptions(value: String): Map<String, String> =
        parseAdvancedOptions(value).associate { it.first to it.second }
}
