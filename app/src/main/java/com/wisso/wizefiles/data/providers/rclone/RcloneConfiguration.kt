package com.wisso.wizefiles.provider.rclone

import org.json.JSONArray
import org.json.JSONObject

data class RcloneProviderDefinition(
    val backendType: String,
    val description: String,
    val options: List<RcloneConfigOption>
)

data class RcloneConfigOption(
    val name: String,
    val help: String,
    val defaultValue: String,
    val examples: List<RcloneConfigExample>,
    val required: Boolean,
    val isPassword: Boolean,
    val type: String,
    val exclusive: Boolean,
    val advanced: Boolean,
    val hidden: Boolean
)

data class RcloneConfigExample(
    val value: String,
    val help: String
) {
    val label: String
        get() = help.lineSequence().firstOrNull().orEmpty().ifBlank { value }
}

data class RcloneConfigStep(
    val state: String,
    val option: RcloneConfigOption?,
    val error: String
) {
    val isComplete: Boolean
        get() = state.isBlank()
}

internal fun parseRcloneProviders(output: JSONObject): List<RcloneProviderDefinition> =
    output.optJSONArray("providers").orEmpty().mapObjects { provider ->
        RcloneProviderDefinition(
            backendType = provider.optString("Name"),
            description = provider.optString("Description")
                .lineSequence()
                .firstOrNull()
                .orEmpty()
                .ifBlank { provider.optString("Name") },
            options = provider.optJSONArray("Options").orEmpty()
                .mapObjects(::parseRcloneConfigOption)
        )
    }.filter { it.backendType.isNotBlank() }

internal fun parseRcloneConfigStep(output: JSONObject): RcloneConfigStep =
    RcloneConfigStep(
        state = output.optString("State"),
        option = output.optJSONObject("Option")?.let(::parseRcloneConfigOption),
        error = output.optString("Error")
    )

internal fun shouldShowRcloneOption(
    option: RcloneConfigOption,
    powerUserMode: Boolean
): Boolean {
    if (option.hidden) {
        return false
    }
    if (powerUserMode) {
        return true
    }
    return !option.advanced && option.name !in TECHNICAL_OPTION_NAMES
}

private fun parseRcloneConfigOption(option: JSONObject): RcloneConfigOption =
    RcloneConfigOption(
        name = option.optString("Name"),
        help = option.optString("Help"),
        defaultValue = option.opt("Default").toDisplayString(),
        examples = option.optJSONArray("Examples").orEmpty().mapObjects { example ->
            RcloneConfigExample(
                value = example.opt("Value").toDisplayString(),
                help = example.optString("Help")
            )
        },
        required = option.optBoolean("Required"),
        isPassword = option.optBoolean("IsPassword"),
        type = option.optString("Type", "string"),
        exclusive = option.optBoolean("Exclusive"),
        advanced = option.optBoolean("Advanced"),
        hidden = option.optInt("Hide", 0) != 0
    )

private fun Any?.toDisplayString(): String =
    when (this) {
        null, JSONObject.NULL -> ""
        is String -> this
        else -> toString()
    }

private val TECHNICAL_OPTION_NAMES = setOf(
    "access_token",
    "access_token_location",
    "auth_url",
    "client_credentials",
    "client_id",
    "client_secret",
    "config_file",
    "config_is_local",
    "config_refresh_token",
    "encoding",
    "refresh_token",
    "scope",
    "service_account_credentials",
    "service_account_file",
    "token",
    "token_location",
    "token_url"
)

private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    List(length()) { index -> transform(getJSONObject(index)) }
