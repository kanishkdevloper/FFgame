package com.dustline.game

import android.graphics.Color

/** The 2 playable characters of the Android build: SURVIVOR and MILITARY SURVIVALIST (the red criminal skin was removed on request, Phase 28). */
class Outfit(val shirt: String, val vest: String, val pants: String, val skin: String, val hair: String, val head: String, val headColor: String)
class Character(val id: String, val name: String, val o: Outfit)

object Characters {
    val ALL = listOf(
        Character("survivor", "SURVIVOR", Outfit("#6a5a3a", "#3b2f1e", "#3b5170", "#c58f6b", "#3b2718", "none", "#3b2f1e")),
        Character("military", "MILITARY SURVIVALIST", Outfit("#556040", "#3d4636", "#4f573d", "#9a6847", "#17110d", "cap", "#4a5436"))
    )
    /** HAIRSTYLES / HAIRS of the wardrobe in the HTML */
    val HAIR_STYLES = listOf("short", "buzz", "tied", "bald")
    val HAIRS = listOf("#17110d", "#2a1c12", "#3d2a1a", "#5e4228", "#8b6a3a", "#101010", "#7a7a78")
    fun defaultHairStyle(id: String) = if (id == "military") "buzz" else "short"
    fun col(s: String) = Color.parseColor(s)
}
