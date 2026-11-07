package com.dustline.game

/**
 * Positions of the round touch buttons. `base()` = the default layout (same numbers the HUD always used),
 * `circle()` = default + the player's own layout from the HUD layout editor (offset as a fraction of the screen, size factor).
 */
object HudLayout {
    val FOOT = intArrayOf(1, 2, 4, 5, 6, 7, 8, 9, 16, 17, 18, 19)
    val DRIVE = intArrayOf(20, 21, 22, 23, 24, 25, 26, 27)
    /** plane + freefall + parachute context (ids 101+ so they are stored apart from the on-foot buttons) */
    val AIR = intArrayOf(101, 102, 119)

    fun label(id: Int): String = when (id) {
        1 -> "JUMP"; 2 -> "RUN"; 4 -> "FIRE"; 5 -> "RELOAD"; 6 -> "HEAL"; 7 -> "AIM"; 8 -> "NADE"; 9 -> "EAT"; 16 -> "USE"; 17 -> "SIT"; 18 -> "BAG"; 19 -> "SOUND"; 101 -> "DROP"; 102 -> "SKATE"; 119 -> "SOUND"
        20 -> "LEFT"; 21 -> "RIGHT"; 22 -> "GAS"; 23 -> "BRAKE"; 24 -> "AUTO"; 25 -> "BOOST"; 26 -> "HORN"; 27 -> "MED"
        else -> "?"
    }

    fun base(id: Int, w: Float, h: Float, d: Float): FloatArray = when (id) {
        16 -> floatArrayOf(w - 335f * d, h - 132f * d, 32f * d)   // USE (enter a vehicle)
        20 -> floatArrayOf(62f * d, h - 62f * d, 40f * d)         // steer left
        21 -> floatArrayOf(156f * d, h - 62f * d, 40f * d)        // steer right
        22 -> floatArrayOf(w - 72f * d, h - 112f * d, 52f * d)    // gas
        23 -> floatArrayOf(w - 176f * d, h - 70f * d, 38f * d)    // brake / reverse
        24 -> floatArrayOf(46f * d, h - 150f * d, 27f * d)        // autopilot
        25 -> floatArrayOf(108f * d, h - 150f * d, 27f * d)       // boost
        26 -> floatArrayOf(170f * d, h - 150f * d, 27f * d)       // horn
        27 -> floatArrayOf(232f * d, h - 150f * d, 27f * d)       // medkit
        17 -> floatArrayOf(w - 190f * d, h - 184f * d, 24f * d)   // SIT / STAND
        101 -> base(1, w, h, d)
        102 -> base(2, w, h, d)   // SKATE / DIVE toggle (HTML toggleDive / V key), only visible in freefall
        119 -> base(19, w, h, d)
        18 -> floatArrayOf(w * 0.5965f, h * 0.8339f + 20f * d, 20f * d)   // BACKPACK (ffBag of the HTML)
        19 -> floatArrayOf(w * 0.731f, h * 0.0459f + 20f * d, 20f * d)    // SOUND on / off (ffSpk)
        1 -> floatArrayOf(w - 190f * d, h - 48f * d, 30f * d)
        2 -> floatArrayOf(w - 190f * d, h - 118f * d, 28f * d)
        4 -> floatArrayOf(w - 82f * d, h - 112f * d, 50f * d)
        9 -> floatArrayOf(w - 262f * d, h - 240f * d, 26f * d)
        6 -> floatArrayOf(w - 135f * d, h - 198f * d, 26f * d)
        7 -> floatArrayOf(w - 262f * d, h - 96f * d, 30f * d)
        8 -> floatArrayOf(w - 262f * d, h - 168f * d, 28f * d)
        else -> floatArrayOf(w - 58f * d, h - 210f * d, 28f * d) // 5 reload
    }

    fun circle(id: Int, w: Float, h: Float, d: Float): FloatArray {
        val b = base(id, w, h, d)
        val a = Prefs.hudGet(id)
        b[0] += a[0] * w; b[1] += a[1] * h; b[2] *= a[2]
        return b
    }
}
