package com.wisso.wizefiles.util

import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.ParcelCompat
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import com.wisso.wizefiles.core.app.appClassLoader
import com.wisso.wizefiles.core.android.compat.readParcelableListCompat

inline fun <reified T : Parcelable> Parcel.readParcelable(): T? =
    ParcelCompat.readParcelable(this, appClassLoader, T::class.java)

fun <T : Parcelable?> Parcel.readParcelableListCompat(classLoader: ClassLoader?): List<T> =
    readParcelableListCompat(mutableListOf(), classLoader)

fun <E : Parcelable?, L : MutableList<E>> Parcel.readParcelableListCompat(list: L): L =
    readParcelableListCompat(list, appClassLoader)

fun <T : Parcelable?> Parcel.readParcelableListCompat(): List<T> =
    readParcelableListCompat(mutableListOf())

@Suppress("UNCHECKED_CAST") fun <T> Parcel.readValue(): T? = readValue(appClassLoader) as T?

@OptIn(ExperimentalContracts::class)
inline fun <R> Parcel.use(block: (Parcel) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return try {
        block(this)
    } finally {
        recycle()
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <R> Parcel.withPosition(position: Int, block: Parcel.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val savedPosition = dataPosition()
    setDataPosition(position)
    return try {
        block(this)
    } finally {
        setDataPosition(savedPosition)
    }
}
