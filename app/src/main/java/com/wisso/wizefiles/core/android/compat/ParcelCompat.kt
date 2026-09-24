package com.wisso.wizefiles.core.android.compat

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.ParcelCompat
import com.wisso.wizefiles.core.app.appClassLoader

fun Parcel.readBooleanCompat(): Boolean = ParcelCompat.readBoolean(this)

fun Parcel.writeBooleanCompat(value: Boolean) {
    ParcelCompat.writeBoolean(this, value)
}

fun <E : Parcelable?, L : MutableList<E>> Parcel.readParcelableListCompat(
    list: L,
    classLoader: ClassLoader?
): L {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        @Suppress("UNCHECKED_CAST")
        return readParcelableList(list as MutableList<Parcelable?>, classLoader, Parcelable::class.java) as L
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        return readParcelableList(list, classLoader) as L
    }
    val size = readInt()
    if (size == -1) {
        list.clear()
        return list
    }
    val listSize = list.size
    for (index in 0 until size) {
        @Suppress("UNCHECKED_CAST")
        val element = ParcelCompat.readParcelable(this, classLoader, Parcelable::class.java) as E
        if (index < listSize) {
            list[index] = element
        } else {
            list += element
        }
    }
    if (size < listSize) {
        list.subList(size, listSize).clear()
    }
    return list
}

fun <T : Parcelable?> Parcel.writeParcelableListCompat(value: List<T>?, flags: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        writeParcelableList(value, flags)
    } else {
        if (value == null) {
            writeInt(-1)
            return
        }
        writeInt(value.size)
        for (element in value) {
            writeParcelable(element, flags)
        }
    }
}

inline fun <reified T : java.io.Serializable> Parcel.readSerializableCompat(): T? =
    ParcelCompat.readSerializable(this, appClassLoader, T::class.java)
