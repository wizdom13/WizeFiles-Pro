package com.wisso.wizefiles.provider.common

import android.os.Parcelable
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class ByteString internal constructor(
    private val storage: ByteArray
) : Comparable<ByteString>, Parcelable {

    val length: Int
        get() = storage.size

    val indices: IntRange
        get() = storage.indices

    val lastIndex: Int
        get() = storage.lastIndex

    operator fun get(index: Int): Byte = storage[index]

    operator fun iterator(): ByteIterator = storage.iterator()

    fun isEmpty(): Boolean = storage.isEmpty()

    fun isNotEmpty(): Boolean = storage.isNotEmpty()

    fun borrowBytes(): ByteArray = storage

    fun toBytes(): ByteArray = storage.copyOf()

    fun startsWith(prefix: ByteString, startIndex: Int = 0): Boolean {
        if (startIndex < 0 || startIndex > length - prefix.length) {
            return false
        }
        var offset = 0
        while (offset < prefix.length) {
            if (storage[startIndex + offset] != prefix.storage[offset]) {
                return false
            }
            ++offset
        }
        return true
    }

    fun endsWith(suffix: ByteString): Boolean =
        startsWith(suffix, length - suffix.length)

    fun indexOf(byte: Byte, fromIndex: Int = 0): Int {
        var index = fromIndex.coerceAtLeast(0)
        while (index < length) {
            if (storage[index] == byte) {
                return index
            }
            ++index
        }
        return NOT_FOUND
    }

    fun lastIndexOf(byte: Byte, fromIndex: Int = lastIndex): Int {
        var index = fromIndex.coerceAtMost(lastIndex)
        while (index >= 0) {
            if (storage[index] == byte) {
                return index
            }
            --index
        }
        return NOT_FOUND
    }

    fun contains(byte: Byte): Boolean = indexOf(byte) != NOT_FOUND

    fun indexOf(substring: ByteString, fromIndex: Int = 0): Int {
        var index = fromIndex.coerceAtLeast(0)
        val finalStart = length - substring.length
        while (index <= finalStart) {
            if (startsWith(substring, index)) {
                return index
            }
            ++index
        }
        return NOT_FOUND
    }

    fun lastIndexOf(substring: ByteString): Int =
        lastIndexOf(substring, length - substring.length)

    fun lastIndexOf(substring: ByteString, fromIndex: Int): Int {
        var index = fromIndex.coerceAtMost(length - substring.length)
        while (index >= 0) {
            if (startsWith(substring, index)) {
                return index
            }
            --index
        }
        return NOT_FOUND
    }

    fun contains(substring: ByteString): Boolean = indexOf(substring) != NOT_FOUND

    fun substring(start: Int, end: Int = length): ByteString {
        requireSlice(start, end)
        if (start == 0 && end == length) {
            return this
        }
        return ByteString(storage.copyOfRange(start, end))
    }

    fun substring(range: IntRange): ByteString =
        substring(range.first, range.last + 1)

    operator fun plus(other: ByteString): ByteString {
        if (other.isEmpty()) {
            return this
        }
        if (isEmpty()) {
            return other
        }
        val combined = ByteArray(length + other.length)
        storage.copyInto(combined)
        other.storage.copyInto(combined, destinationOffset = length)
        return ByteString(combined)
    }

    fun split(delimiter: ByteString): List<ByteString> {
        require(delimiter.isNotEmpty())
        val parts = ArrayList<ByteString>()
        var start = 0
        while (start <= length) {
            val match = indexOf(delimiter, start)
            if (match == NOT_FOUND) {
                parts += substring(start)
                break
            }
            parts += substring(start, match)
            start = match + delimiter.length
        }
        return parts
    }

    @IgnoredOnParcel
    private var decoded: String? = null

    override fun toString(): String {
        decoded?.let { return it }
        return String(storage).also { decoded = it }
    }

    val cstr: ByteArray
        get() = storage.copyOf(length + 1)

    override fun equals(other: Any?): Boolean =
        other is ByteString && storage.contentEquals(other.storage)

    override fun hashCode(): Int = storage.contentHashCode()

    override fun compareTo(other: ByteString): Int {
        val commonLength = minOf(length, other.length)
        var index = 0
        while (index < commonLength) {
            val difference = storage[index] - other.storage[index]
            if (difference != 0) {
                return difference
            }
            ++index
        }
        return length - other.length
    }

    private fun requireSlice(start: Int, end: Int) {
        if (start < 0 || end < start || end > length) {
            throw IndexOutOfBoundsException(
                "Invalid byte range [" + start + ", " + end + ") for length " + length
            )
        }
    }

    companion object {
        val EMPTY = ByteString(ByteArray(0))

        fun fromBytes(bytes: ByteArray, start: Int = 0, end: Int = bytes.size): ByteString =
            ByteString(bytes.copyOfRange(start, end))

        fun takeBytes(bytes: ByteArray): ByteString = ByteString(bytes)

        fun fromString(string: String): ByteString =
            ByteString(string.toByteArray()).also { it.decoded = string }

        private const val NOT_FOUND = -1
    }
}

fun Byte.toByteString(): ByteString = ByteString.takeBytes(byteArrayOf(this))

fun ByteArray.toByteString(start: Int = 0, end: Int = size): ByteString =
    ByteString.fromBytes(this, start, end)

fun ByteArray.moveToByteString(): ByteString = ByteString.takeBytes(this)

fun String.toByteString(): ByteString = ByteString.fromString(this)

@OptIn(ExperimentalContracts::class)
fun ByteString?.isNullOrEmpty(): Boolean {
    contract { returns(false) implies (this@isNullOrEmpty != null) }
    return this == null || isEmpty()
}

fun ByteString.takeIfNotEmpty(): ByteString? = takeIf(ByteString::isNotEmpty)

fun ByteString.drop(n: Int): ByteString {
    require(n >= 0)
    return substring(n.coerceAtMost(length))
}

fun ByteString.dropLast(n: Int): ByteString {
    require(n >= 0)
    return substring(0, (length - n).coerceAtLeast(0))
}

inline fun ByteString.dropLastWhile(predicate: (Byte) -> Boolean): ByteString {
    var end = length
    while (end > 0 && predicate(this[end - 1])) {
        --end
    }
    return substring(0, end)
}

inline fun ByteString.dropWhile(predicate: (Byte) -> Boolean): ByteString {
    var start = 0
    while (start < length && predicate(this[start])) {
        ++start
    }
    return substring(start)
}

fun ByteString.take(n: Int): ByteString {
    require(n >= 0)
    return substring(0, n.coerceAtMost(length))
}

fun ByteString.takeLast(n: Int): ByteString {
    require(n >= 0)
    return substring(length - n.coerceAtMost(length))
}

inline fun ByteString.takeLastWhile(predicate: (Byte) -> Boolean): ByteString {
    var start = length
    while (start > 0 && predicate(this[start - 1])) {
        --start
    }
    return substring(start)
}

inline fun ByteString.takeWhile(predicate: (Byte) -> Boolean): ByteString {
    var end = 0
    while (end < length && predicate(this[end])) {
        ++end
    }
    return substring(0, end)
}

fun ByteString.substringBefore(
    delimiter: Byte,
    missingDelimiterValue: ByteString = this
): ByteString = sliceAround(indexOf(delimiter), before = true, 1, missingDelimiterValue)

fun ByteString.substringBefore(
    delimiter: ByteString,
    missingDelimiterValue: ByteString = this
): ByteString =
    sliceAround(indexOf(delimiter), before = true, delimiter.length, missingDelimiterValue)

fun ByteString.substringAfter(
    delimiter: Byte,
    missingDelimiterValue: ByteString = this
): ByteString = sliceAround(indexOf(delimiter), before = false, 1, missingDelimiterValue)

fun ByteString.substringAfter(
    delimiter: ByteString,
    missingDelimiterValue: ByteString = this
): ByteString =
    sliceAround(indexOf(delimiter), before = false, delimiter.length, missingDelimiterValue)

fun ByteString.substringBeforeLast(
    delimiter: Byte,
    missingDelimiterValue: ByteString = this
): ByteString = sliceAround(lastIndexOf(delimiter), before = true, 1, missingDelimiterValue)

fun ByteString.substringBeforeLast(
    delimiter: ByteString,
    missingDelimiterValue: ByteString = this
): ByteString =
    sliceAround(lastIndexOf(delimiter), before = true, delimiter.length, missingDelimiterValue)

fun ByteString.substringAfterLast(
    delimiter: Byte,
    missingDelimiterValue: ByteString = this
): ByteString = sliceAround(lastIndexOf(delimiter), before = false, 1, missingDelimiterValue)

fun ByteString.substringAfterLast(
    delimiter: ByteString,
    missingDelimiterValue: ByteString = this
): ByteString =
    sliceAround(lastIndexOf(delimiter), before = false, delimiter.length, missingDelimiterValue)

private fun ByteString.sliceAround(
    match: Int,
    before: Boolean,
    delimiterLength: Int,
    missing: ByteString
): ByteString {
    if (match < 0) {
        return missing
    }
    return if (before) substring(0, match) else substring(match + delimiterLength)
}
