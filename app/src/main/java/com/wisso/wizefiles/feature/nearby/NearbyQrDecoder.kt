// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.nio.ByteBuffer

/** Reads the camera's luminance plane, including padded rows and rotated sensors. */
internal object NearbyQrDecoder {
    fun decode(plane: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int): String? {
        require(width in 1..1920 && height in 1..1920 && pixelStride > 0 && rowStride >= width)
        val source = plane.duplicate()
        val base = source.position()
        val last = base.toLong() + (height - 1L) * rowStride + (width - 1L) * pixelStride
        require(last < source.limit()) { "Truncated camera frame" }
        val luminance = ByteArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            luminance[y * width + x] = source.get(base + y * rowStride + x * pixelStride)
        }
        val image = PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false)
        val reader = QRCodeReader()
        val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)
        for (candidate in listOf(image, image.invert())) {
            try { return reader.decode(BinaryBitmap(HybridBinarizer(candidate)), hints).text }
            catch (_: ReaderException) { reader.reset() }
        }
        return null
    }
}
