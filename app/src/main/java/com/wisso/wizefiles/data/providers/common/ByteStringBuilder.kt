package com.wisso.wizefiles.provider.common

class ByteStringBuilder(capacity: Int = DEFAULT_CAPACITY) {
    private var buffer = ByteArray(capacity)

    var length: Int = 0
        private set

    constructor(byteString: ByteString) : this(byteString.length + DEFAULT_CAPACITY) {
        append(byteString)
    }

    operator fun get(index: Int): Byte {
        if (index < 0 || index >= length) {
            throw IndexOutOfBoundsException(
                "Index " + index + " is outside builder length " + length
            )
        }
        return buffer[index]
    }

    val isEmpty: Boolean
        get() = length == 0

    fun capacity(): Int = buffer.size

    fun append(byte: Byte): ByteStringBuilder = apply {
        reserve(1)
        buffer[length] = byte
        ++length
    }

    fun append(
        bytes: ByteArray,
        start: Int = 0,
        end: Int = bytes.size
    ): ByteStringBuilder = apply {
        requireSourceRange(bytes, start, end)
        val count = end - start
        reserve(count)
        bytes.copyInto(
            destination = buffer,
            destinationOffset = length,
            startIndex = start,
            endIndex = end
        )
        length += count
    }

    fun append(byteString: ByteString): ByteStringBuilder =
        append(byteString.borrowBytes())

    fun toByteString(): ByteString = ByteString.fromBytes(buffer, 0, length)

    override fun toString(): String = buffer.decodeToString(endIndex = length)

    private fun reserve(additionalBytes: Int) {
        val required = length + additionalBytes
        if (required <= buffer.size) {
            return
        }
        val expanded = maxOf(required, buffer.size * 2 + 2)
        buffer = buffer.copyOf(expanded)
    }

    private fun requireSourceRange(bytes: ByteArray, start: Int, end: Int) {
        if (start < 0 || end < start || end > bytes.size) {
            throw IndexOutOfBoundsException(
                "Invalid source range [" + start + ", " + end + ") for length " + bytes.size
            )
        }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 16
    }
}
