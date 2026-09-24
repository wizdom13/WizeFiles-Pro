package com.wisso.wizefiles.provider.common

import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel

fun ReadableByteChannel.newInputStream(): InputStream {
    return Channels.newInputStream(this)
}

fun WritableByteChannel.newOutputStream(): OutputStream {
    return Channels.newOutputStream(this)
}
