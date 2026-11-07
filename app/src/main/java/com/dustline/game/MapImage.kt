package com.dustline.game

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Top-down picture of the island for the mini-map and the full-screen map, rendered from the height-field of the
 * Bermuda map (sea / land shaded by height + slope). World x/z in [-half, half] maps to pixel 0..SIZE.
 */
class MapImage(map: MapData) {
    companion object { const val SIZE = 512 }
    val half: Float = abs(map.x0)
    val bmp: Bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)

    init {
        val px = IntArray(SIZE * SIZE)
        val step = half * 2f / SIZE
        for (j in 0 until SIZE) for (i in 0 until SIZE) {
            val x = -half + (i + 0.5f) * step; val z = -half + (j + 0.5f) * step
            val h = map.ground(x, z)
            var r: Int; var g: Int; var b: Int
            if (h <= 0.05f) {
                val deep = (map.ground(x - step * 2, z) + map.ground(x + step * 2, z) + map.ground(x, z - step * 2) + map.ground(x, z + step * 2)) / 4f
                val k = if (deep > 0.05f) 1f else 0f   // shallow water next to the coast
                r = (22 + 14 * k).toInt(); g = (74 + 36 * k).toInt(); b = (104 + 30 * k).toInt()
            } else {
                val t = (h / 70f).coerceIn(0f, 1f)
                r = (84 + 100 * t).toInt(); g = (122 + 40 * t).toInt(); b = (58 + 66 * t).toInt()
                val sl = (map.ground(x - step, z - step) - map.ground(x + step, z + step)) * 0.9f
                r = (r + sl).toInt().coerceIn(0, 255); g = (g + sl).toInt().coerceIn(0, 255); b = (b + sl).toInt().coerceIn(0, 255)
            }
            px[j * SIZE + i] = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        }
        bmp.setPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
    }
}
