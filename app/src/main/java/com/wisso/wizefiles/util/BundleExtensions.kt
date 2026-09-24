package com.wisso.wizefiles.util

import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.BundleCompat
import com.wisso.wizefiles.core.app.appClassLoader

fun <T : Parcelable> Bundle.getParcelableSafe(key: String?, valueClass: Class<T>): T? {
    classLoader = appClassLoader
    return BundleCompat.getParcelable(this, key, valueClass)
}

inline fun <reified T : Parcelable> Bundle.getParcelableSafe(key: String?): T? =
    getParcelableSafe(key, T::class.java)

fun <T : java.io.Serializable> Bundle.getSerializableSafe(key: String?, valueClass: Class<T>): T? {
    classLoader = appClassLoader
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializable(key, valueClass)
    } else {
        @Suppress("DEPRECATION")
        getSerializable(key)?.let { valueClass.takeIf { clazz -> clazz.isInstance(it) }?.cast(it) }
    }
}

inline fun <reified T : java.io.Serializable> Bundle.getSerializableSafe(key: String?): T? {
    classLoader = appClassLoader
    return getSerializableSafe(key, T::class.java)
}

fun <T : Any> Bundle.requireSerializableList(key: String?, itemClass: Class<T>): List<T>? {
    val values = getSerializableSafe(key, java.io.Serializable::class.java) ?: return null
    if (values !is List<*>) {
        return null
    }
    return values.mapNotNull { item -> itemClass.takeIf { it.isInstance(item) }?.cast(item) }
        .takeIf { it.size == values.size }
}
