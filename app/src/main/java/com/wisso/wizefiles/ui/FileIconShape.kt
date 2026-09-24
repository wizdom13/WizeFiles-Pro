// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.ui

import android.graphics.Matrix
import android.graphics.Path
import androidx.annotation.StringRes
import androidx.core.graphics.PathParser
import com.wisso.wizefiles.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class FileIconShape(
    val stableId: String,
    @StringRes val titleRes: Int,
    private val pathData: String? = null,
    private val polygonSides: Int = 0,
    private val polygonRotationDegrees: Float = -90f
) {
    SQUIRCLE(
        "squircle",
        R.string.settings_icon_shape_squircle,
        "M50,0 C10,0 0,10 0,50 C0,90 10,100 50,100 C90,100 100,90 100,50 C100,10 90,0 50,0 Z"
    ),
    ROUNDED_SQUARE(
        "rounded_square",
        R.string.settings_icon_shape_rounded_square,
        "M50,0 L88,0 C94.627,0 100,5.373 100,12 L100,88 C100,94.627 94.627,100 88,100 L12,100 C5.373,100 0,94.627 0,88 L0,12 C0,5.373 5.373,0 12,0 Z"
    ),
    FLOWER(
        "flower",
        R.string.settings_icon_shape_flower,
        "M50,0 C60.6,0 69.9,5.3 75.6,13.5 78.5,17.8 82.3,21.5 86.6,24.5 94.7,30.1 100,39.4 100,50 100,60.6 94.7,69.9 86.5,75.6 82.2,78.5 78.5,82.3 75.5,86.6 69.9,94.7 60.6,100 50,100 39.4,100 30.1,94.7 24.4,86.5 21.5,82.2 17.7,78.5 13.4,75.5 5.3,69.9 0,60.6 0,50 0,39.4 5.3,30.1 13.5,24.4 17.8,21.5 21.5,17.7 24.5,13.4 30.1,5.3 39.4,0 50,0 Z"
    ),
    SQUARE(
        "square",
        R.string.settings_icon_shape_square,
        "M0,0 L100,0 L100,100 L0,100 Z"
    ),
    TEARDROP(
        "teardrop",
        R.string.settings_icon_shape_teardrop,
        "M50,0 C77.614,0 100,22.386 100,50 L100,100 L50,100 C22.386,100 0,77.614 0,50 C0,22.386 22.386,0 50,0 Z"
    ),
    PEBBLE(
        "pebble",
        R.string.settings_icon_shape_pebble,
        "M55,0 C25,0 0,25 0,50 0,78 28,100 55,100 85,100 100,85 100,58 100,30 86,0 55,0 Z"
    ),
    VESSEL(
        "vessel",
        R.string.settings_icon_shape_vessel,
        "M12.97,0 C8.41,0 4.14,2.55 2.21,6.68 -1.03,13.61 -0.71,21.78 3.16,28.46 4.89,31.46 4.89,35.2 3.16,38.2 -1.05,45.48 -1.05,54.52 3.16,61.8 4.89,64.8 4.89,68.54 3.16,71.54 -0.71,78.22 -1.03,86.39 2.21,93.32 4.14,97.45 8.41,100 12.97,100 21.38,100 78.62,100 87.03,100 91.59,100 95.85,97.45 97.79,93.32 101.02,86.39 100.71,78.22 96.84,71.54 95.1,68.54 95.1,64.8 96.84,61.8 101.05,54.52 101.05,45.48 96.84,38.2 95.1,35.2 95.1,31.46 96.84,28.46 100.71,21.78 101.02,13.61 97.79,6.68 95.85,2.55 91.59,0 87.03,0 78.62,0 21.38,0 12.97,0 Z"
    ),
    PENTAGON("pentagon", R.string.settings_icon_shape_pentagon, polygonSides = 5),
    HEXAGON_1(
        "hexagon_1",
        R.string.settings_icon_shape_hexagon_1,
        polygonSides = 6,
        polygonRotationDegrees = 0f
    ),
    HEXAGON_2(
        "hexagon_2",
        R.string.settings_icon_shape_hexagon_2,
        polygonSides = 6,
        polygonRotationDegrees = -90f
    ),
    HEPTAGON("heptagon", R.string.settings_icon_shape_heptagon, polygonSides = 7),
    OCTAGON(
        "octagon",
        R.string.settings_icon_shape_octagon,
        polygonSides = 8,
        polygonRotationDegrees = -112.5f
    );

    private val normalizedPath: Path by lazy {
        pathData?.let { PathParser.createPathFromPathData(it) }
            ?: createRegularPolygon(polygonSides, polygonRotationDegrees)
    }

    fun createPath(width: Int, height: Int): Path {
        if (width <= 0 || height <= 0) return Path()
        val size = min(width, height).toFloat()
        val matrix = Matrix().apply {
            setScale(size / MASK_SIZE, size / MASK_SIZE)
            postTranslate((width - size) / 2f, (height - size) / 2f)
        }
        return Path(normalizedPath).apply { transform(matrix) }
    }

    companion object {
        val DEFAULT: FileIconShape = SQUIRCLE
        val stableIds: Set<String> = entries.mapTo(linkedSetOf()) { it.stableId }

        fun fromStableId(value: String?): FileIconShape =
            entries.firstOrNull { it.stableId == value } ?: DEFAULT

        private const val MASK_SIZE = 100f

        private fun createRegularPolygon(sides: Int, rotationDegrees: Float): Path {
            require(sides >= 3)
            val path = Path()
            repeat(sides) { index ->
                val angle = (rotationDegrees + index * 360f / sides) * PI / 180.0
                val x = 50f + 50f * cos(angle).toFloat()
                val y = 50f + 50f * sin(angle).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            return path
        }
    }
}
