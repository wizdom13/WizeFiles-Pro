package com.wisso.wizefiles.settings

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Parcel
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.appClassLoader
import com.wisso.wizefiles.core.app.defaultSharedPreferences
import com.wisso.wizefiles.core.app.secretStore
import com.wisso.wizefiles.feature.filebrowser.FileSortOptions
import com.wisso.wizefiles.feature.filebrowser.GridColumnOverrides
import com.wisso.wizefiles.navigation.BookmarkDirectory
import com.wisso.wizefiles.navigation.StandardDirectorySettings
import com.wisso.wizefiles.searchindex.SearchIndexManager
import com.wisso.wizefiles.security.AppSecurityManager
import com.wisso.wizefiles.security.SecretStore
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLocalFileOrNull
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.ui.FileIconShape
import com.wisso.wizefiles.util.asBase64
import com.wisso.wizefiles.util.toBase64
import com.wisso.wizefiles.util.toByteArray
import com.wisso.wizefiles.util.use
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONException
import org.json.JSONObject

internal class SettingsRestoreValidator(
    private val context: Context,
    private val keySet: Set<String>,
    private val hasSecurityPassword: () -> Boolean,
    private val hasRootCapability: () -> Boolean = {
        runCatching { com.topjohnwu.superuser.Shell.getShell().isRoot }.getOrDefault(false)
    },
    private val keyResolver: (Int) -> String = { context.getString(it) }
) {
    private data class RestorePolicy(
        val validate: (BackupValue) -> Unit,
        val normalize: (BackupValue) -> BackupValue = { it },
        val strictValidation: Boolean = false,
        val prerequisite: ((BackupValue) -> Boolean)? = null,
        val skipReason: String? = null,
        val warning: String? = null
    )

    private val restorePolicies: Map<String, RestorePolicy> by lazy { buildPolicies() }

    fun buildPreview(data: SettingsBackupData): SettingsRestorePreview {
        val missingPolicies = keySet - restorePolicies.keys
        if (missingPolicies.isNotEmpty()) {
            throw IllegalStateException("Missing restore policy for keys: ${missingPolicies.sorted().joinToString()}")
        }
        val extraPolicies = restorePolicies.keys - keySet
        if (extraPolicies.isNotEmpty()) {
            throw IllegalStateException("Unexpected restore policies for keys: ${extraPolicies.sorted().joinToString()}")
        }
        val apply = linkedMapOf<String, BackupValue>()
        val skipped = linkedMapOf<String, String>()
        val warnings = mutableListOf<String>()
        var hasCompatibilitySkips = false
        data.settings.forEach { (key, value) ->
            val policy = restorePolicies[key]
            if (policy == null) {
                skipped[key] = OBSOLETE_SETTING_REASON
                hasCompatibilitySkips = true
                return@forEach
            }
            val normalizedValue = try {
                policy.normalize(value).also { policy.validate(it) }
            } catch (exception: IllegalArgumentException) {
                if (policy.strictValidation) {
                    throw exception
                }
                skipped[key] = INCOMPATIBLE_SETTING_REASON
                hasCompatibilitySkips = true
                return@forEach
            }
            if (policy.prerequisite != null && !policy.prerequisite.invoke(normalizedValue)) {
                skipped[key] = policy.skipReason ?: "Prerequisite not satisfied"
                policy.warning?.let(warnings::add)
                return@forEach
            }
            apply[key] = normalizedValue
        }
        if (hasCompatibilitySkips) {
            warnings.add(0, COMPATIBILITY_WARNING)
        }
        return SettingsRestorePreview(
            schemaVersion = data.formatVersion,
            settingsToApply = apply,
            skippedSettings = skipped,
            warnings = warnings
        )
    }

    private fun buildPolicies(): Map<String, RestorePolicy> = mapOf(
        keyResolver(R.string.pref_key_file_list_default_directory) to RestorePolicy(
            validate = ::validateDefaultDirectoryParcel,
            strictValidation = true
        ),
        keyResolver(R.string.pref_key_file_list_persistent_drawer_open) to RestorePolicy(::validateBoolean),
        keyResolver(R.string.pref_key_file_list_show_hidden_files) to RestorePolicy(::validateBoolean),
        keyResolver(R.string.pref_key_file_list_view_type) to RestorePolicy(validate = { validateStringAllowlist(it, setOf("0", "1")) }),
        keyResolver(R.string.pref_key_file_list_grid_column_overrides) to
            RestorePolicy(::validateGridColumnOverridesParcel),
        keyResolver(R.string.pref_key_file_list_sort_options) to RestorePolicy(
            validate = ::validateSortOptions,
            normalize = ::normalizeSortOptions
        ),
        keyResolver(R.string.pref_key_create_archive_type) to RestorePolicy(validate = {
            validateIntAllowlist(it, setOf(R.id.zipRadio, R.id.tarXzRadio, R.id.sevenZRadio))
        }),
        keyResolver(R.string.pref_key_locale) to RestorePolicy(::validateLocale),
        keyResolver(R.string.pref_key_night_mode) to RestorePolicy(validate = { validateStringAllowlist(it, setOf("0", "1", "2", "3", "4")) }),
        keyResolver(R.string.pref_key_black_night_mode) to RestorePolicy(::validateBoolean),
        keyResolver(R.string.pref_key_file_icon_shape) to RestorePolicy(validate = {
            validateStringAllowlist(it, FileIconShape.stableIds)
        }),
        keyResolver(R.string.pref_key_file_list_animation) to RestorePolicy(::validateBoolean),
        keyResolver(R.string.pref_key_file_name_ellipsize) to RestorePolicy(validate = { validateStringAllowlist(it, setOf("0", "1", "2", "3")) }),
        keyResolver(R.string.pref_key_standard_directory_settings) to RestorePolicy(
            validate = ::validateStandardDirectorySettingsParcel,
            strictValidation = true
        ),
        keyResolver(R.string.pref_key_bookmark_directories) to RestorePolicy(
            validate = ::validateBookmarkDirectoriesParcel,
            strictValidation = true
        ),
        keyResolver(R.string.pref_key_recycle_bin) to RestorePolicy(::validateBoolean),
        keyResolver(R.string.pref_key_root_strategy) to RestorePolicy(
            validate = { validateStringAllowlist(it, setOf("0", "1", "2")) },
            strictValidation = true,
            prerequisite = { value -> (value.value as String) != "2" || hasRootCapability() },
            skipReason = "Root capability is not available",
            warning = "Skipped root strategy restore because root capability is unavailable."
        ),
        keyResolver(R.string.pref_key_archive_file_name_encoding) to RestorePolicy(::validateCharsetName),
        keyResolver(R.string.pref_key_open_apk_default_action) to RestorePolicy(validate = { validateStringAllowlist(it, setOf("0", "1", "2")) }),
        keyResolver(R.string.pref_key_show_pdf_thumbnail_pre_28) to RestorePolicy(::validateBoolean),
        keyResolver(R.string.pref_key_read_remote_files_for_thumbnail) to RestorePolicy(::validateBoolean),
        keyResolver(R.string.pref_key_indexed_search) to RestorePolicy(::validateBoolean),
        keyResolver(R.string.pref_key_protect_browser) to securityBooleanPolicy(),
        keyResolver(R.string.pref_key_enable_biometric) to securityBooleanPolicy(),
        keyResolver(R.string.pref_key_time_to_relock) to RestorePolicy(
            validate = { validateStringAllowlist(it, setOf("0", "30000", "60000", "300000", "600000")) },
            strictValidation = true
        ),
        keyResolver(R.string.pref_key_sftp_pinned_host_keys) to RestorePolicy(
            validate = ::validatePinnedHostKeys,
            strictValidation = true
        )
    )

    private fun securityBooleanPolicy(): RestorePolicy = RestorePolicy(
        validate = ::validateBoolean,
        strictValidation = true,
        prerequisite = { value -> value.value != true || hasSecurityPassword() },
        skipReason = "Security password is not configured",
        warning = "Skipped security setting because password is not configured."
    )

    private fun validateBoolean(value: BackupValue) {
        if (value.type != "boolean" || value.value !is Boolean) {
            throw IllegalArgumentException("Invalid boolean setting value")
        }
    }

    private fun validateIntAllowlist(value: BackupValue, allowed: Set<Int>) {
        if (value.type != "int" || value.value !is Number || value.value.toInt() !in allowed) {
            throw IllegalArgumentException("Invalid integer setting value")
        }
    }

    private fun validateStringAllowlist(value: BackupValue, allowed: Set<String>) {
        if (value.type != "string") {
            throw IllegalArgumentException("Invalid enum setting value")
        }
        val stringValue = value.value as? String ?: throw IllegalArgumentException("Invalid enum setting value")
        if (stringValue !in allowed) {
            throw IllegalArgumentException("Invalid enum setting value")
        }
    }


    private fun validateSortOptions(value: BackupValue) {
        if (value.type != "file_sort_options_v2") {
            throw IllegalArgumentException("Invalid file sort setting value")
        }
        val payload = value.value as? JSONObject ?: throw IllegalArgumentException("Invalid file sort setting value")
        rejectUnexpectedObjectKeys(payload, setOf("by", "order", "isDirectoriesFirst"), "Invalid file sort setting value")
        val by = payload.optString("by")
        val order = payload.optString("order")
        if (FileSortOptions.By.entries.none { it.name == by } ||
            FileSortOptions.Order.entries.none { it.name == order }) {
            throw IllegalArgumentException("Invalid file sort setting value")
        }
        if (payload.has("isDirectoriesFirst") && payload.opt("isDirectoriesFirst") !is Boolean) {
            throw IllegalArgumentException("Invalid file sort setting value")
        }
    }

    private fun normalizeSortOptions(value: BackupValue): BackupValue {
        if (value.type == "file_sort_options_v2") {
            return value
        }
        val legacyParcel = value.value as? String
        if (value.type != "string" || legacyParcel == null ||
            legacyParcel.length > MAX_LEGACY_SORT_VALUE_LENGTH) {
            return value
        }
        return BackupValue(
            type = "file_sort_options_v2",
            value = JSONObject()
                .put("by", FileSortOptions.By.NAME.name)
                .put("order", FileSortOptions.Order.ASCENDING.name)
                .put("isDirectoriesFirst", true)
        )
    }

    private fun validateLocale(value: BackupValue) {
        if (value.type != "string") {
            throw IllegalArgumentException("Invalid locale setting value")
        }
        val locale = value.value as? String ?: return
        if (locale.length > 35 || locale.any { it.isISOControl() } || !locale.matches(Regex("^[A-Za-z0-9_\\-]*$"))) {
            throw IllegalArgumentException("Invalid locale setting value")
        }
    }

    private fun validateCharsetName(value: BackupValue) {
        if (value.type != "string") {
            throw IllegalArgumentException("Invalid charset setting value")
        }
        val charsetName = value.value as? String ?: throw IllegalArgumentException("Invalid charset setting value")
        if (charsetName.length > 64 || charsetName.any { it.isISOControl() }) {
            throw IllegalArgumentException("Invalid charset setting value")
        }
        runCatching { java.nio.charset.Charset.forName(charsetName) }
            .getOrElse { throw IllegalArgumentException("Invalid charset setting value") }
    }

    private fun validatePinnedHostKeys(value: BackupValue) {
        if (value.type != "string") {
            throw IllegalArgumentException("Invalid pinned-host-keys value")
        }
        val raw = value.value as? String ?: throw IllegalArgumentException("Invalid pinned-host-keys value")
        if (raw.length > 16_384 || raw.any { it == '\u0000' }) {
            throw IllegalArgumentException("Invalid pinned-host-keys value")
        }
        val parsed = runCatching { JSONObject(raw) }
            .getOrElse { throw IllegalArgumentException("Invalid pinned-host-keys value") }
        parsed.keys().forEach { host ->
            if (host.length > 255 || host.any { it.isISOControl() }) {
                throw IllegalArgumentException("Invalid pinned-host-keys value")
            }
            if (parsed.optString(host).isBlank()) {
                throw IllegalArgumentException("Invalid pinned-host-keys value")
            }
        }
    }

    private fun validateGridColumnOverridesParcel(value: BackupValue) {
        val bytes = requireSerializedParcelBytes(value)
        val overrides = decodeSingleParcelValue(bytes) as? GridColumnOverrides
            ?: throw IllegalArgumentException("Invalid grid column override setting value")
        val allowed = GridColumnOverrides.MANUAL_RANGE + GridColumnOverrides.AUTO
        if (overrides.compact !in allowed || overrides.wide !in allowed) {
            throw IllegalArgumentException("Invalid grid column override setting value")
        }
    }

    private fun validateDefaultDirectoryParcel(value: BackupValue) {
        val bytes = requireSerializedParcelBytes(value)
        val parcelValue = decodeSingleParcelValue(bytes)
        when (parcelValue) {
            is AppPath -> validatePathLikeValue(parcelValue.rawPath, allowAbsolute = true)
            is java.nio.file.Path -> validatePathLikeValue(parcelValue.toString(), allowAbsolute = true)
            is String -> validatePathLikeValue(parcelValue, allowAbsolute = true)
            else -> throw IllegalArgumentException("Invalid default directory setting value")
        }
    }

    private fun validateStandardDirectorySettingsParcel(value: BackupValue) {
        val bytes = requireSerializedParcelBytes(value)
        val rawEntries = decodeSingleParcelValue(bytes) as? ArrayList<*>
            ?: throw IllegalArgumentException("Invalid standard directory setting value")
        val entries = rawEntries.map {
            it as? StandardDirectorySettings ?: throw IllegalArgumentException("Invalid standard directory setting value")
        }
        if (entries.size > 128) {
            throw IllegalArgumentException("Invalid standard directory setting value")
        }
        val ids = mutableSetOf<String>()
        entries.forEach { entry ->
            if (!isValidRelativeDirectoryId(entry.id) || !ids.add(entry.id)) {
                throw IllegalArgumentException("Invalid standard directory setting value")
            }
            if (entry.customTitle != null && !isValidTitle(entry.customTitle)) {
                throw IllegalArgumentException("Invalid standard directory setting value")
            }
        }
    }

    private fun validateBookmarkDirectoriesParcel(value: BackupValue) {
        val bytes = requireSerializedParcelBytes(value)
        val rawEntries = decodeSingleParcelValue(bytes) as? ArrayList<*>
            ?: throw IllegalArgumentException("Invalid bookmark directory setting value")
        val entries = rawEntries.map {
            it as? BookmarkDirectory ?: throw IllegalArgumentException("Invalid bookmark directory setting value")
        }
        if (entries.size > 512) {
            throw IllegalArgumentException("Invalid bookmark directory setting value")
        }
        val ids = mutableSetOf<Long>()
        entries.forEach { entry ->
            if (!ids.add(entry.id)) {
                throw IllegalArgumentException("Invalid bookmark directory setting value")
            }
            if (entry.customName != null && !isValidTitle(entry.customName)) {
                throw IllegalArgumentException("Invalid bookmark directory setting value")
            }
            validatePathLikeValue(entry.path.rawPath, allowAbsolute = true)
        }
    }

    private fun requireSerializedParcelBytes(value: BackupValue): ByteArray {
        if (value.type != "string") {
            throw IllegalArgumentException("Invalid serialized setting value")
        }
        val raw = value.value as? String ?: throw IllegalArgumentException("Invalid serialized setting value")
        if (raw.length > 131_072 || raw.any { it.isISOControl() && it != '\n' && it != '\t' }) {
            throw IllegalArgumentException("Invalid serialized setting value")
        }
        val decoded = runCatching { raw.asBase64().toByteArray() }
            .getOrElse { throw IllegalArgumentException("Invalid serialized setting value") }
        if (decoded.isEmpty() || decoded.size > 65_536) {
            throw IllegalArgumentException("Invalid serialized setting value")
        }
        return decoded
    }

    private fun decodeSingleParcelValue(bytes: ByteArray): Any? {
        return Parcel.obtain().use { parcel ->
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            // Untrusted backup payloads are decoded into a single value and must consume the
            // parcel completely. Any trailing bytes are treated as malformed payload data.
            val value = runCatching { parcel.readValue(appClassLoader ?: javaClass.classLoader) }
                .getOrElse { throw IllegalArgumentException("Invalid serialized setting value") }
            if (parcel.dataAvail() != 0) {
                throw IllegalArgumentException("Invalid serialized setting value")
            }
            value
        }
    }

    private fun rejectUnexpectedObjectKeys(payload: JSONObject, allowedKeys: Set<String>, errorMessage: String) {
        payload.keys().forEach { key ->
            if (key !in allowedKeys) {
                throw IllegalArgumentException(errorMessage)
            }
        }
    }

    private fun isValidRelativeDirectoryId(id: String): Boolean {
        if (id.isBlank() || id.length > 512) return false
        if (id.startsWith("/") || id.startsWith("\\")) return false
        if (id.contains("..") || id.contains("%2e", ignoreCase = true)) return false
        return id.none { it.isISOControl() || it == '\u0000' }
    }

    private fun isValidTitle(value: String): Boolean =
        value.length <= 120 &&
            value.none { it.isISOControl() && it != '\n' && it != '\t' }

    private fun validatePathLikeValue(path: String, allowAbsolute: Boolean) {
        if (path.isBlank() || path.length > 4096) {
            throw IllegalArgumentException("Invalid serialized setting value")
        }
        if (path.any { it == '\u0000' || (it.isISOControl() && it != '\n' && it != '\t') }) {
            throw IllegalArgumentException("Invalid serialized setting value")
        }
        val normalized = path.lowercase()
        if (hasDoubleEncodedUriScheme(normalized) || normalized.contains("%00") || normalized.contains("%2e%2e")) {
            throw IllegalArgumentException("Invalid serialized setting value")
        }
        if (normalized.contains("/../") || normalized.contains("\\..\\") || normalized == "..") {
            throw IllegalArgumentException("Invalid serialized setting value")
        }
        if (path.startsWith("\\\\") || Regex("^[A-Za-z]:\\\\").containsMatchIn(path)) {
            throw IllegalArgumentException("Invalid serialized setting value")
        }
        if (!allowAbsolute && path.startsWith("/")) {
            throw IllegalArgumentException("Invalid serialized setting value")
        }
        val uri = runCatching { Uri.parse(path) }.getOrNull()
        if (!uri?.scheme.isNullOrBlank()) {
            val scheme = uri?.scheme ?: throw IllegalArgumentException("Invalid serialized setting value")
            if (!scheme.matches(Regex("^[A-Za-z][A-Za-z0-9+.-]{0,31}$"))) {
                throw IllegalArgumentException("Invalid serialized setting value")
            }
            if (uri.isOpaque) {
                throw IllegalArgumentException("Invalid serialized setting value")
            }
            if (uri.userInfo?.isNotBlank() == true) {
                throw IllegalArgumentException("Invalid serialized setting value")
            }
            if (uri.host?.length ?: 0 > 255) {
                throw IllegalArgumentException("Invalid serialized setting value")
            }
        }
    }

    private fun hasDoubleEncodedUriScheme(normalizedPath: String): Boolean {
        if (!normalizedPath.contains("%25")) return false
        val fullyDecoded = runCatching { Uri.decode(Uri.decode(normalizedPath)) }.getOrNull() ?: return true
        return Regex("^[a-z][a-z0-9+.-]{0,31}://").containsMatchIn(fullyDecoded)
    }
}

private const val MAX_LEGACY_SORT_VALUE_LENGTH = 16_384
private const val OBSOLETE_SETTING_REASON = "Setting is no longer supported"
private const val INCOMPATIBLE_SETTING_REASON = "Incompatible setting value"
private const val COMPATIBILITY_WARNING =
    "Some settings were skipped because they are obsolete or incompatible."
