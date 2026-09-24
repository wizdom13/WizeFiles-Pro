package com.wisso.wizefiles.settings

import android.content.SharedPreferences
import android.os.Parcel
import androidx.annotation.AnyRes
import androidx.annotation.BoolRes
import androidx.annotation.IntegerRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import com.wisso.wizefiles.R
import com.wisso.wizefiles.feature.filebrowser.FileSortOptions
import com.wisso.wizefiles.core.app.appClassLoader
import com.wisso.wizefiles.core.app.application
import com.wisso.wizefiles.security.SecretReferenceReconciler
import com.wisso.wizefiles.security.SecretStore
import com.wisso.wizefiles.util.Base64
import com.wisso.wizefiles.util.asBase64
import com.wisso.wizefiles.util.getBoolean
import com.wisso.wizefiles.util.getInteger
import com.wisso.wizefiles.util.toBase64
import com.wisso.wizefiles.util.toByteArray
import com.wisso.wizefiles.util.use
import com.wisso.wizefiles.core.app.secretStore as globalSecretStore

private val fileListViewTypeSettingKey by lazy {
    application.getString(R.string.pref_key_file_list_view_type)
}
private val fileListGridColumnOverridesSettingKey by lazy {
    application.getString(R.string.pref_key_file_list_grid_column_overrides)
}
private val fileListSortOptionsSettingKey by lazy {
    application.getString(R.string.pref_key_file_list_sort_options)
}
private val fileListViewSortPathSpecificSettingKey by lazy {
    application.getString(R.string.pref_key_file_list_view_sort_path_specific)
}

private const val FILE_SORT_OPTIONS_STABLE_PREFIX = "sort_v2:"
private const val FILE_SORT_OPTIONS_SEPARATOR = "|"

private fun shouldCommitSynchronously(key: String): Boolean =
    key == fileListViewTypeSettingKey ||
        key.startsWith("${fileListViewTypeSettingKey}_") ||
        key == fileListGridColumnOverridesSettingKey ||
        key.startsWith("${fileListGridColumnOverridesSettingKey}_") ||
        key == fileListSortOptionsSettingKey ||
        key.startsWith("${fileListSortOptionsSettingKey}_") ||
        key == fileListViewSortPathSpecificSettingKey ||
        key.startsWith("${fileListViewSortPathSpecificSettingKey}_")

class StringSettingLiveData(
    nameSuffix: String?,
    @StringRes keyRes: Int,
    keySuffix: String?,
    @StringRes defaultValueRes: Int
) : SettingLiveData<String>(nameSuffix, keyRes, keySuffix, defaultValueRes) {
    constructor(@StringRes keyRes: Int, @StringRes defaultValueRes: Int) : this(
        null, keyRes, null, defaultValueRes
    )

    init {
        init()
    }

    override fun getDefaultValue(@StringRes defaultValueRes: Int): String =
        application.getString(defaultValueRes)

    override fun getValue(
        sharedPreferences: SharedPreferences,
        key: String,
        defaultValue: String
    ): String = sharedPreferences.getString(key, defaultValue)!!

    override fun putValue(sharedPreferences: SharedPreferences, key: String, value: String) {
        sharedPreferences.edit { putString(key, value) }
    }
}

class IntegerSettingLiveData(
    nameSuffix: String?,
    @StringRes keyRes: Int,
    keySuffix: String?,
    @IntegerRes defaultValueRes: Int
) : SettingLiveData<Int>(nameSuffix, keyRes, keySuffix, defaultValueRes) {
    constructor(@StringRes keyRes: Int, @IntegerRes defaultValueRes: Int) : this(
        null, keyRes, null, defaultValueRes
    )

    init {
        init()
    }

    override fun getDefaultValue(@IntegerRes defaultValueRes: Int): Int =
        application.getInteger(defaultValueRes)

    override fun getValue(
        sharedPreferences: SharedPreferences,
        key: String,
        defaultValue: Int
    ): Int = sharedPreferences.getInt(key, defaultValue)

    override fun putValue(sharedPreferences: SharedPreferences, key: String, value: Int) {
        sharedPreferences.edit { putInt(key, value) }
    }
}

class BooleanSettingLiveData(
    nameSuffix: String?,
    @StringRes keyRes: Int,
    keySuffix: String?,
    @BoolRes defaultValueRes: Int
) : SettingLiveData<Boolean>(nameSuffix, keyRes, keySuffix, defaultValueRes) {
    constructor(@StringRes keyRes: Int, @BoolRes defaultValueRes: Int) : this(
        null, keyRes, null, defaultValueRes
    )

    init {
        init()
    }

    override fun getDefaultValue(@BoolRes defaultValueRes: Int): Boolean =
        application.getBoolean(defaultValueRes)

    override fun getValue(
        sharedPreferences: SharedPreferences,
        key: String,
        defaultValue: Boolean
    ): Boolean = sharedPreferences.getBoolean(key, defaultValue)

    override fun putValue(sharedPreferences: SharedPreferences, key: String, value: Boolean) {
        sharedPreferences.edit(commit = shouldCommitSynchronously(key)) { putBoolean(key, value) }
    }
}

// Use string resource for default value so that we can support ListPreference.
// TODO: kotlinc: Type argument is not within its bounds: should be subtype of 'Enum<E>'
//  https://youtrack.jetbrains.com/issue/KT-60985
//class EnumSettingLiveData<E : Enum<E>?>(
class EnumSettingLiveData<E : Enum<*>?>(
    nameSuffix: String?,
    @StringRes keyRes: Int,
    keySuffix: String?,
    @StringRes defaultValueRes: Int,
    enumClass: Class<E>
) : SettingLiveData<E>(nameSuffix, keyRes, keySuffix, defaultValueRes) {
    private val enumValues = enumClass.enumConstants!!

    constructor(
        @StringRes keyRes: Int,
        @StringRes defaultValueRes: Int,
        enumClass: Class<E>
    ) : this(null, keyRes, null, defaultValueRes, enumClass)

    init {
        init()
    }

    override fun getDefaultValue(@StringRes defaultValueRes: Int): E =
        if (defaultValueRes != ResourcesCompat.ID_NULL) {
            enumValues[application.getString(defaultValueRes).toInt()]
        } else {
            @Suppress("UNCHECKED_CAST")
            null as E
        }

    override fun getValue(
        sharedPreferences: SharedPreferences,
        key: String,
        defaultValue: E
    ): E {
        val valueOrdinal = sharedPreferences.getString(key, null)?.toInt() ?: return defaultValue
        return if (valueOrdinal in enumValues.indices) enumValues[valueOrdinal] else defaultValue
    }

    override fun putValue(sharedPreferences: SharedPreferences, key: String, value: E) {
        sharedPreferences.edit(commit = shouldCommitSynchronously(key)) {
            putString(key, value?.ordinal?.toString())
        }
    }
}

class ResourceIdSettingLiveData(
    nameSuffix: String?,
    @StringRes keyRes: Int,
    keySuffix: String?,
    @AnyRes defaultValue: Int
) : SettingLiveData<Int>(nameSuffix, keyRes, keySuffix, defaultValue) {
    constructor(@StringRes keyRes: Int, @AnyRes defaultValue: Int) : this(
        null, keyRes, null, defaultValue
    )

    init {
        init()
    }

    @AnyRes
    override fun getDefaultValue(@AnyRes defaultValueRes: Int): Int = defaultValueRes

    override fun getValue(
        sharedPreferences: SharedPreferences,
        key: String,
        @AnyRes defaultValue: Int
    ): Int {
        val valueString = sharedPreferences.getString(key, null) ?: return defaultValue
        val value = application.resources.getIdentifier(valueString, null, application.packageName)
        return if (value != 0) value else defaultValue
    }

    override fun putValue(sharedPreferences: SharedPreferences, key: String, @AnyRes value: Int) {
        sharedPreferences.edit { putString(key, application.resources.getResourceName(value)) }
    }
}

class ParcelValueSettingLiveData<T>(
    nameSuffix: String?,
    @StringRes keyRes: Int,
    keySuffix: String?,
    private val defaultValue: T,
    private val secretStoreProvider: () -> SecretStore = { globalSecretStore },
    private val sanitizer: (T) -> T = { it },
    private val classLoaderProvider: () -> ClassLoader? = { appClassLoader },
    private val normalizer: (Any?) -> Any? = { it }
) : SettingLiveData<T>(nameSuffix, keyRes, keySuffix, 0) {
    constructor(@StringRes keyRes: Int, defaultValue: T) : this(
        null,
        keyRes,
        null,
        defaultValue
    )

    constructor(@StringRes keyRes: Int, defaultValue: T, sanitizer: (T) -> T) : this(
        null,
        keyRes,
        null,
        defaultValue,
        sanitizer = sanitizer
    )

    constructor(
        @StringRes keyRes: Int,
        defaultValue: T,
        sanitizer: (T) -> T,
        classLoaderProvider: () -> ClassLoader?,
        normalizer: (Any?) -> Any?
    ) : this(
        null,
        keyRes,
        null,
        defaultValue,
        sanitizer = sanitizer,
        classLoaderProvider = classLoaderProvider,
        normalizer = normalizer
    )

    init {
        init()
    }

    override fun getDefaultValue(@AnyRes defaultValueRes: Int): T = defaultValue

    override fun getValue(
        sharedPreferences: SharedPreferences,
        key: String,
        defaultValue: T
    ): T {
        val serializedValue = sharedPreferences.getString(key, null)
        if (isFileSortOptionsKey(key)) {
            val sortDefaultValue = defaultValue as? FileSortOptions ?: FileSortOptions(
                by = FileSortOptions.By.NAME,
                order = FileSortOptions.Order.ASCENDING,
                isDirectoriesFirst = true
            )
            val currentSortOptions = decodeCurrentFileSortOptions(
                serializedValue = serializedValue,
                defaultValue = sortDefaultValue
            )
            if (currentSortOptions != null) {
                val normalizedSerializedValue = encodeStableFileSortOptions(currentSortOptions)
                if (serializedValue != normalizedSerializedValue) {
                    sharedPreferences.edit(commit = shouldCommitSynchronously(key)) {
                        putString(key, normalizedSerializedValue)
                    }
                }
                @Suppress("UNCHECKED_CAST")
                return currentSortOptions as T
            }
            return defaultValue
        }
        val currentValue = try {
            serializedValue?.asBase64()?.toParcelValue(classLoaderProvider, normalizer)
        } catch (e: Exception) {
            null
        }
        return sanitizer(currentValue ?: defaultValue)
    }

    override fun putValue(sharedPreferences: SharedPreferences, key: String, value: T) {
        val oldSerializedValue = sharedPreferences.getString(key, null)
        val sanitizedValue = sanitizer(value)
        val newSerializedValue = if (isFileSortOptionsKey(key) && sanitizedValue is FileSortOptions) {
            encodeStableFileSortOptions(sanitizedValue)
        } else {
            serializeToParcelBase64(sanitizedValue).value
        }
        sharedPreferences.edit(commit = shouldCommitSynchronously(key)) {
            putString(key, newSerializedValue)
        }
        SecretReferenceReconciler.removeStaleReferences(
            oldSerializedValue = oldSerializedValue,
            newSerializedValue = newSerializedValue,
            secretStoreProvider = secretStoreProvider
        )
    }

    private fun decodeCurrentFileSortOptions(
        serializedValue: String?,
        defaultValue: FileSortOptions
    ): FileSortOptions? {
        return decodeStableFileSortOptions(serializedValue)
            ?: decodeStoredFileSortOptions(serializedValue, defaultValue)
    }

    private fun decodeStableFileSortOptions(serializedValue: String?): FileSortOptions? {
        val value = serializedValue?.takeIf { it.startsWith(FILE_SORT_OPTIONS_STABLE_PREFIX) } ?: return null
        val payload = value.removePrefix(FILE_SORT_OPTIONS_STABLE_PREFIX)
        val parts = payload.split(FILE_SORT_OPTIONS_SEPARATOR)
        if (parts.size != 3) {
            return null
        }
        val by = FileSortOptions.By.entries.firstOrNull { it.name == parts[0] } ?: return null
        val order = FileSortOptions.Order.entries.firstOrNull { it.name == parts[1] } ?: return null
        val directoriesFirst = when (parts[2]) {
            "1", "true", "TRUE" -> true
            "0", "false", "FALSE" -> false
            else -> return null
        }
        return FileSortOptions(
            by = by,
            order = order,
            isDirectoriesFirst = directoriesFirst
        )
    }

    private fun encodeStableFileSortOptions(value: FileSortOptions): String =
        buildString {
            append(FILE_SORT_OPTIONS_STABLE_PREFIX)
            append(value.by.name)
            append(FILE_SORT_OPTIONS_SEPARATOR)
            append(value.order.name)
            append(FILE_SORT_OPTIONS_SEPARATOR)
            append(if (value.isDirectoriesFirst) "1" else "0")
        }

    private fun decodeStoredFileSortOptions(
        serializedValue: String?,
        defaultValue: FileSortOptions?
    ): FileSortOptions? {
        val bytes = runCatching { serializedValue?.asBase64()?.toByteArray() }.getOrNull() ?: return null
        val fallbackValue = defaultValue ?: FileSortOptions(
            by = FileSortOptions.By.NAME,
            order = FileSortOptions.Order.ASCENDING,
            isDirectoriesFirst = true
        )
        return parseLegacyFileSortOptions(bytes, skipLeadingTypeMarker = true, fallbackValue = fallbackValue)
            ?: parseLegacyFileSortOptions(bytes, skipLeadingTypeMarker = false, fallbackValue = fallbackValue)
            ?: decodeSortOptionsFromStringScan(bytes, fallbackValue)
    }

    private fun isFileSortOptionsKey(key: String): Boolean =
        key == fileListSortOptionsSettingKey ||
            key.startsWith("${fileListSortOptionsSettingKey}_")

    private fun parseLegacyFileSortOptions(
        bytes: ByteArray,
        skipLeadingTypeMarker: Boolean,
        fallbackValue: FileSortOptions
    ): FileSortOptions? {
        return Parcel.obtain().use { parcel ->
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            if (skipLeadingTypeMarker) {
                runCatching { parcel.readInt() }.getOrElse { return@use null }
            }
            var byToken = runCatching { parcel.readString() }.getOrNull() ?: return@use null
            if (byToken == FileSortOptions::class.java.name) {
                byToken = runCatching { parcel.readString() }.getOrNull() ?: return@use null
            }
            val by = FileSortOptions.By.entries.firstOrNull { it.name == byToken } ?: return@use null
            val orderToken = runCatching { parcel.readString() }.getOrNull()
            val order = FileSortOptions.Order.entries.firstOrNull { it.name == orderToken }
                ?: fallbackValue.order
            val directoriesFirst = when {
                parcel.dataAvail() <= 0 -> fallbackValue.isDirectoriesFirst
                else -> {
                    val positionBeforeBoolean = parcel.dataPosition()
                    runCatching { parcel.readInt() != 0 }
                        .recoverCatching {
                            parcel.setDataPosition(positionBeforeBoolean)
                            parcel.readByte().toInt() != 0
                        }
                        .getOrDefault(fallbackValue.isDirectoriesFirst)
                }
            }
            FileSortOptions(
                by = by,
                order = order,
                isDirectoriesFirst = directoriesFirst
            )
        }
    }

    private fun decodeSortOptionsFromStringScan(
        bytes: ByteArray,
        fallbackValue: FileSortOptions
    ): FileSortOptions? {
        val normalized = buildString(bytes.size) {
            bytes.forEach { byte ->
                append((byte.toInt() and 0xFF).toChar())
            }
        }.replace("\u0000", "")
        val by = FileSortOptions.By.entries.firstOrNull { normalized.contains(it.name) } ?: return null
        val order = FileSortOptions.Order.entries.firstOrNull { normalized.contains(it.name) }
            ?: fallbackValue.order
        val lastMeaningfulByte = bytes.lastOrNull { it.toInt() != 0 }?.toInt()?.and(0xFF)
        val directoriesFirst = when (lastMeaningfulByte) {
            0 -> false
            1 -> true
            else -> fallbackValue.isDirectoriesFirst
        }
        return FileSortOptions(
            by = by,
            order = order,
            isDirectoriesFirst = directoriesFirst
        )
    }

    private fun Base64.toParcelValue(
        classLoaderProvider: () -> ClassLoader?,
        normalizer: (Any?) -> Any?
    ): T {
        @Suppress("UNCHECKED_CAST")
        return normalizer(toRawParcelValue(classLoaderProvider)) as T
    }

    private fun Base64.toRawParcelValue(classLoaderProvider: () -> ClassLoader?): Any? {
        val bytes = toByteArray()
        return Parcel.obtain().use { parcel ->
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            parcel.readValue(classLoaderProvider())
        }
    }

    private fun serializeToParcelBase64(value: Any?): Base64 {
        val bytes = Parcel.obtain().use { parcel ->
            parcel.writeValue(value)
            parcel.marshall()
        }
        return bytes.toBase64()
    }
}
