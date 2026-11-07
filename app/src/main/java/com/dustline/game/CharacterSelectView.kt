package com.dustline.game

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

/** #charSel: top profile bar + title, card per character (portrait), BACK button. */
class CharacterSelectView(ctx: Context) : View(ctx) {
    var account: Account? = null
    var profile: Profile = Profile()
    var onBack: (() -> Unit)? = null
    var onPick: ((Character) -> Unit)? = null
    var onHair: (() -> Unit)? = null
    private val styleR = Array(Characters.HAIR_STYLES.size) { RectF() }
    private val colR = Array(Characters.HAIRS.size) { RectF() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bar = ProfileBar()
    private val cards = Array(Characters.ALL.size) { RectF() }
    private val backRect = RectF()
    private var pressed = -1

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val d = resources.displayMetrics.density
        paint.shader = null
        c.drawColor(0xFF14110D.toInt())
        paint.shader = RadialGradient(w * 0.7f, h * 0.3f, w * 0.6f, 0xFF6B5D4A.toInt(), 0x006B5D4A, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h, paint)
        paint.shader = RadialGradient(w * 0.2f, h * 0.8f, w * 0.55f, 0xFF4A4034.toInt(), 0x004A4034, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val pbh = (h * 0.12f).coerceIn(44f * d, 60f * d)
        val topH = pbh + 12f * d
        paint.shader = LinearGradient(0f, 0f, w, 0f, 0xEB0A0910.toInt(), 0xB30A0910.toInt(), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, topH, paint); paint.shader = null
        bar.draw(c, account, 12f * d, 6f * d, pbh, w, d)
        paint.typeface = Ui.FONT_BOLD; paint.textSize = 22f * d; paint.letterSpacing = 0.14f; paint.color = Color.WHITE
        paint.textAlign = Paint.Align.RIGHT; paint.textSkewX = -0.2f
        c.drawText("CHARACTER", w - 14f * d, topH / 2 + 8f * d, paint)
        paint.textSkewX = 0f; paint.textAlign = Paint.Align.LEFT; paint.letterSpacing = 0f

        val n = Characters.ALL.size
        val pad = maxOf(24f * d, w * 0.06f); val gap = 14f * d
        val cw = (w - pad * 2 - gap * (n - 1)) / n
        val top = topH + 22f * d; val ch = h - top - 104f * d
        for (i in 0 until n) {
            val l = pad + i * (cw + gap)
            val r = RectF(l, top, l + cw, top + ch)
            cards[i].set(r)
            val chr = Characters.ALL[i]; val sel = profile.char == chr.id
            paint.color = 0xFFB38A62.toInt(); c.drawRect(r, paint)
            paint.color = 0xFFE6C9A4.toInt(); c.drawRect(r.left, r.top, r.right, r.top + 3f * d, paint)
            val pt = RectF(r.left, r.top + 3f * d, r.right, r.bottom - 34f * d)
            paint.color = 0xFF8A6845.toInt(); c.drawPath(Ui.polygon(pt.left, pt.top, pt.right, pt.top, pt.left, pt.bottom), paint)
            paint.color = 0xFF5D442B.toInt(); c.drawPath(Ui.polygon(pt.right, pt.top, pt.right, pt.bottom, pt.left, pt.bottom), paint)
            PortraitRenderer.draw(c, chr, pt, if (sel) profile.hairStyle.ifEmpty { null } else null, if (sel) profile.hair.ifEmpty { null } else null)
            paint.color = if (sel) Ui.GOLD else 0x99000000.toInt(); c.drawRect(r.left, r.bottom - 34f * d, r.right, r.bottom, paint)
            paint.typeface = Ui.FONT_BOLD; paint.textSize = 14f * d; paint.letterSpacing = 0.1f; paint.textAlign = Paint.Align.CENTER
            paint.color = if (sel) Ui.INK else Color.WHITE
            c.drawText(chr.name, r.centerX(), r.bottom - 12f * d, paint)
            paint.textAlign = Paint.Align.LEFT; paint.letterSpacing = 0f
            if (sel) { paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f * d; paint.color = Ui.GOLD; c.drawRect(r, paint); paint.style = Paint.Style.FILL }
        }

        // wardrobe: HAIR STYLE + HAIR COLOUR (profile.hairStyle / profile.hair, saved on the device)
        run {
            val curStyle = profile.hairStyle.ifEmpty { Characters.defaultHairStyle(profile.char) }
            val x0 = 160f * d; val rowH = 30f * d; val y1 = h - 86f * d; val y2 = h - 46f * d
            paint.typeface = Ui.FONT_BOLD; paint.textSize = 10f * d; paint.letterSpacing = 0.1f; paint.textAlign = Paint.Align.LEFT; paint.color = 0xFFB9B2C6.toInt()
            c.drawText("HAIR STYLE", x0, y1 - 4f * d, paint); c.drawText("HAIR COLOUR", x0, y2 - 4f * d, paint)
            val bwid = 64f * d
            for (i in Characters.HAIR_STYLES.indices) {
                val r = styleR[i]; r.set(x0 + i * (bwid + 6f * d), y1, x0 + i * (bwid + 6f * d) + bwid, y1 + rowH)
                val sel = Characters.HAIR_STYLES[i] == curStyle
                paint.style = Paint.Style.FILL; paint.color = if (sel) Ui.GOLD else 0xCC0A0910.toInt(); c.drawRect(r, paint)
                paint.color = if (sel) Ui.INK else Color.WHITE; paint.textAlign = Paint.Align.CENTER; paint.textSize = 12f * d
                c.drawText(Characters.HAIR_STYLES[i].uppercase(), r.centerX(), r.centerY() + 4f * d, paint)
            }
            val curCol = profile.hair.ifEmpty { Characters.ALL.firstOrNull { it.id == profile.char }?.o?.hair ?: "" }
            for (i in Characters.HAIRS.indices) {
                val r = colR[i]; r.set(x0 + i * (rowH + 6f * d), y2, x0 + i * (rowH + 6f * d) + rowH, y2 + rowH)
                paint.style = Paint.Style.FILL; paint.color = Characters.col(Characters.HAIRS[i]); c.drawRect(r, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = (if (Characters.HAIRS[i].equals(curCol, true)) 3f else 1f) * d
                paint.color = if (Characters.HAIRS[i].equals(curCol, true)) Ui.GOLD else 0x88FFFFFF.toInt(); c.drawRect(r, paint)
            }
            paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.LEFT; paint.letterSpacing = 0f
        }
        val bw = 130f * d; val bh = 40f * d
        backRect.set(12f * d, h - bh - 12f * d, 12f * d + bw, h - 12f * d)
        paint.color = if (pressed == 100) Ui.GOLD else 0xE60A0910.toInt()
        c.drawPath(Ui.polygon(backRect.left, backRect.top, backRect.right, backRect.top, backRect.right - 12f * d, backRect.bottom, backRect.left, backRect.bottom), paint)
        paint.color = Ui.GOLD; c.drawRect(backRect.left, backRect.top, backRect.left + 4f * d, backRect.bottom, paint)
        paint.typeface = Ui.FONT_BOLD; paint.textSize = 17f * d; paint.letterSpacing = 0.1f
        paint.color = if (pressed == 100) Ui.INK else Color.WHITE
        c.drawText("‹ BACK", backRect.left + 18f * d, backRect.centerY() + 6f * d, paint)
        paint.typeface = Ui.FONT_MED; paint.textSize = 12f * d; paint.color = 0xFFB9B2C6.toInt()
        c.drawText("SELECT YOUR CHARACTER", backRect.right + 16f * d, backRect.centerY() + 4f * d, paint)
        paint.letterSpacing = 0f
    }

    private fun hit(x: Float, y: Float): Int {
        if (backRect.contains(x, y)) return 100
        for (i in cards.indices) if (cards[i].contains(x, y)) return i
        for (i in styleR.indices) if (styleR[i].contains(x, y)) return 200 + i
        for (i in colR.indices) if (colR[i].contains(x, y)) return 300 + i
        return -1
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { pressed = hit(e.x, e.y); invalidate() }
            MotionEvent.ACTION_UP -> {
                val p = pressed; pressed = -1; invalidate()
                if (p >= 0 && hit(e.x, e.y) == p) {
                    when {
                        p == 100 -> onBack?.invoke()
                        p in 200..299 -> { profile.hairStyle = Characters.HAIR_STYLES[p - 200]; onHair?.invoke(); invalidate() }
                        p in 300..399 -> { profile.hair = Characters.HAIRS[p - 300]; onHair?.invoke(); invalidate() }
                        else -> onPick?.invoke(Characters.ALL[p])
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> { pressed = -1; invalidate() }
        }
        return true
    }
}
