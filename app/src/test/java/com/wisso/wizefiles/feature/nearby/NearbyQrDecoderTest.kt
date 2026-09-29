// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class NearbyQrDecoderTest {
    @Test fun decodesPairingQrFromPaddedCameraPlaneInEveryOrientation() {
        val value = NearbyQrAuthentication.encode(ByteArray(32) { it.toByte() })
        val size = 256
        val qr = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
        repeat(4) { rotation ->
            val stride = size * 2 + 32
            val frame = ByteArray(stride * size + 7) { 0xff.toByte() }
            for (y in 0 until size) for (x in 0 until size) {
                val (sx, sy) = when (rotation) { 1 -> y to size - x - 1; 2 -> size - x - 1 to size - y - 1; 3 -> size - y - 1 to x; else -> x to y }
                frame[7 + y * stride + x * 2] = if (qr[sx, sy]) 0 else 0xff.toByte()
            }
            val plane = ByteBuffer.wrap(frame).apply { position(7) }
            assertEquals(value, NearbyQrDecoder.decode(plane, size, size, stride, 2))
            assertEquals(7, plane.position())
        }
    }
    @Test fun blankCameraFrameIsNotAResult() {
        assertNull(NearbyQrDecoder.decode(ByteBuffer.wrap(ByteArray(256 * 256) { 0xff.toByte() }), 256, 256, 256, 1))
    }
    @Test(expected = IllegalArgumentException::class) fun truncatedPlaneIsRejected() {
        NearbyQrDecoder.decode(ByteBuffer.wrap(ByteArray(100)), 256, 256, 256, 1)
    }
}
