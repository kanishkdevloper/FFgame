package com.dustline.game

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

/**
 * STORE: 4 tops, 99 diamonds each (STORE_PRICE) - tap to select, BUY (confirm) / WEAR / REMOVE, like renderStore()/storeBuy().
 * VAULT: OUTFITS (the 3 characters + MY CLOTHES bought in the store), POWER ("NO POWER-UPS AVAILABLE YET"),
 * OTHERS (RESET OUTFIT). Bought clothes + the worn one are saved in the account (account.store).
 */
class StoreView(ctx: Context) : View(ctx) {
    class Item(val id: String, val name: String, val file: String)
    companion object {
        const val PRICE = 99
        val ITEMS = listOf(
            Item("t-shirt", "RAGLAN TEE", "store/t-shirt.jpg"), Item("sweater_pack", "OLIVE SWEATER", "store/sweater_pack.jpg"),
            Item("t-shirt___lp", "HENLEY TEE", "store/t-shirt___lp.jpg"), Item("t-shirt_3d_model_free", "WHITE TEE", "store/t-shirt_3d_model_free.jpg"))
    }

    var account: Account? = null
    var profile: Profile = Profile()
    var vault = false
    var onClose: (() -> Unit)? = null
    var onChanged: (() -> Unit)? = null      // account / profile changed -> save
    var onToast: ((String) -> Unit)? = null
    var onPreview: ((String?) -> Unit)? = null   // 3D preview: top shown on the avatar (selected item, else the worn one)
    var onRotate: ((Float) -> Unit)? = null      // drag on the right panel turns the 3D avatar
    private var lastPv: String? = "?"; private var dragX = 0f; private var inPanel = false

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bar = ProfileBar()
    private val thumbs = HashMap<String, Bitmap?>()
    private val tiles = ArrayList<Pair<RectF, () -> Unit>>()
    private val backR = RectF(); private var vtab = 0; private var sel: String? = null
    private var confirm: Item? = null; private val yesR = RectF(); private val noR = RectF()
    private var scroll = 0f; private var dragY = 0f; private var moved = false; private var maxScroll = 0f

    private fun thumb(i: Item): Bitmap? = thumbs.getOrPut(i.id) {
        try { context.assets.open(i.file).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 }) } } catch (e: Exception) { null }
    }

    fun open(asVault: Boolean) { vault = asVault; vtab = 0; sel = null; confirm = null; scroll = 0f; lastPv = "?"; invalidate() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val d = resources.displayMetrics.density
        tiles.clear()
        // right 30 % stays transparent: the 3D avatar (LobbyAvatarView, panel mode) shows through it
        val cw = w * 0.70f
        val pv = if (!vault) (sel ?: account?.eq) else account?.eq
        if (pv != lastPv) { lastPv = pv; onPreview?.invoke(pv) }
        p.color = 0xFF14110D.toInt(); p.style = Paint.Style.FILL; c.drawRect(0f, 0f, cw, h, p)
        p.shader = RadialGradient(cw * 0.7f, h * 0.3f, cw * 0.6f, 0xFF6B5D4A.toInt(), 0x006B5D4A, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, cw, h, p); p.shader = null
        val pbh = (h * 0.12f).coerceIn(44f * d, 60f * d); val topH = pbh + 12f * d
        p.color = 0xE60A0910.toInt(); c.drawRect(0f, 0f, w, topH, p)
        bar.draw(c, account, 12f * d, 6f * d, pbh, w, d)
        p.typeface = Ui.FONT_BOLD; p.textSize = 22f * d; p.color = Color.WHITE; p.textAlign = Paint.Align.RIGHT; p.letterSpacing = 0.14f
        c.drawText(if (vault) "VAULT" else "STORE", w - 14f * d, topH / 2 + 8f * d, p); p.letterSpacing = 0f; p.textAlign = Paint.Align.LEFT
        backR.set(12f * d, h - 52f * d, 142f * d, h - 12f * d)
        p.color = Ui.GOLD; c.drawRect(backR, p); p.color = Ui.INK; p.textSize = 17f * d; p.textAlign = Paint.Align.CENTER; c.drawText("‹ BACK", backR.centerX(), backR.centerY() + 6f * d, p)
        var y0 = topH + 10f * d
        if (vault) { // tabs
            val names = listOf("OUTFITS", "POWER", "OTHERS"); var x = 14f * d
            for (i in names.indices) {
                val r = RectF(x, y0, x + 110f * d, y0 + 34f * d); p.color = if (i == vtab) Ui.GOLD else 0xFF2A2438.toInt(); c.drawRect(r, p)
                p.color = if (i == vtab) Ui.INK else Color.WHITE; p.textSize = 14f * d; c.drawText(names[i], r.centerX(), r.centerY() + 5f * d, p)
                val ii = i; tiles.add(Pair(r) { vtab = ii; scroll = 0f }); x += 116f * d
            }
            y0 += 44f * d
        }
        p.textAlign = Paint.Align.LEFT
        val top = y0; val bottom = h - 60f * d
        c.save(); c.clipRect(0f, top, cw, bottom)
        var y = top - scroll
        val acc = account
        if (!vault) y = drawStore(c, cw, y, d, acc)
        else when (vtab) {
            0 -> y = drawOutfits(c, cw, y, d, acc)
            1 -> y = note(c, "NO POWER-UPS AVAILABLE YET.", y, d)
            else -> {
                y = note(c, "DRAG ON THE CHARACTER TO ROTATE IT. RESET PUTS THE CURRENT OUTFIT BACK TO ITS ORIGINAL LOOK.", y, d)
                val r = RectF(16f * d, y, 216f * d, y + 40f * d); p.color = Ui.GOLD; c.drawRect(r, p); p.color = Ui.INK; p.textAlign = Paint.Align.CENTER; p.textSize = 15f * d
                c.drawText("RESET OUTFIT", r.centerX(), r.centerY() + 5f * d, p); p.textAlign = Paint.Align.LEFT
                tiles.add(Pair(r) { account?.eq = null; sel = null; onChanged?.invoke(); onToast?.invoke("OUTFIT RESET") }); y += 50f * d
            }
        }
        c.restore()
        maxScroll = (y + scroll - bottom).coerceAtLeast(0f)
        // preview caption under the 3D avatar
        p.typeface = Ui.FONT_BOLD; p.textAlign = Paint.Align.CENTER; p.textSize = 12f * d; p.color = Ui.GOLD
        val pcx = w - (w - cw) / 2f
        val prevItem = if (!vault) ITEMS.find { it.id == sel } else null
        if (prevItem != null && account?.eq != prevItem.id) c.drawText("PREVIEW: " + prevItem.name, pcx, h - 34f * d, p)
        p.color = 0xFFD8CFC0.toInt(); p.textSize = 11f * d; c.drawText("DRAG TO ROTATE", pcx, h - 16f * d, p); p.textAlign = Paint.Align.LEFT
        confirm?.let { drawConfirm(c, w, h, d, it) }
    }

    private fun note(c: Canvas, t: String, y: Float, d: Float): Float {
        p.typeface = Ui.FONT_MED; p.textSize = 13f * d; p.color = 0xFFD8CFC0.toInt(); p.textAlign = Paint.Align.LEFT
        c.drawText(t, 16f * d, y + 18f * d, p); return y + 34f * d
    }

    private fun tile(c: Canvas, r: RectF, bmp: Bitmap?, label: String, sub: String, on: Boolean, d: Float, act: () -> Unit) {
        p.style = Paint.Style.FILL; p.color = if (on) 0xFFD9A441.toInt() else 0xFFB38A62.toInt(); c.drawRect(r, p)
        if (bmp != null) { val s = minOf(r.width() * 0.84f / bmp.width, (r.height() - 40f * d) / bmp.height); val bw = bmp.width * s; val bh = bmp.height * s
            c.drawBitmap(bmp, null, RectF(r.centerX() - bw / 2, r.top + 6f * d, r.centerX() + bw / 2, r.top + 6f * d + bh), p) }
        p.color = 0xAA000000.toInt(); c.drawRect(r.left, r.bottom - 34f * d, r.right, r.bottom, p)
        p.typeface = Ui.FONT_BOLD; p.textAlign = Paint.Align.CENTER; p.color = Color.WHITE; p.textSize = 13f * d; c.drawText(label, r.centerX(), r.bottom - 19f * d, p)
        p.textSize = 11f * d; p.color = Ui.GOLD; c.drawText(sub, r.centerX(), r.bottom - 6f * d, p)
        if (on) { p.style = Paint.Style.STROKE; p.strokeWidth = 3f * d; p.color = Ui.GOLD; c.drawRect(r, p); p.style = Paint.Style.FILL }
        p.textAlign = Paint.Align.LEFT; tiles.add(Pair(r, act))
    }

    private fun drawStore(c: Canvas, w: Float, y0: Float, d: Float, a: Account?): Float {
        var y = y0
        val cur = ITEMS.find { it.id == sel }
        val br = RectF(14f * d, y, w - 14f * d, y + 46f * d); p.style = Paint.Style.FILL; p.color = 0xEB14161D.toInt(); c.drawRect(br, p); p.color = Ui.GOLD; c.drawRect(br.left, br.top, br.left + 4f * d, br.bottom, p)
        p.typeface = Ui.FONT_BOLD; p.textSize = 16f * d; p.color = Color.WHITE
        if (cur == null) c.drawText("TAP A T-SHIRT TO SELECT IT", br.left + 16f * d, br.centerY() + 6f * d, p)
        else {
            val owned = a?.owned?.contains(cur.id) == true; val worn = a?.eq == cur.id
            c.drawText(cur.name, br.left + 16f * d, br.centerY() + 6f * d, p)
            p.color = Ui.GOLD; c.drawText(if (owned) "OWNED" else "◆ $PRICE", br.left + 200f * d, br.centerY() + 6f * d, p)
            val ab = RectF(br.right - 130f * d, br.top + 6f * d, br.right - 8f * d, br.bottom - 6f * d); p.color = Ui.GOLD; c.drawRect(ab, p)
            p.color = Ui.INK; p.textAlign = Paint.Align.CENTER; c.drawText(if (owned) (if (worn) "REMOVE" else "WEAR") else "BUY", ab.centerX(), ab.centerY() + 6f * d, p); p.textAlign = Paint.Align.LEFT
            tiles.add(Pair(ab) { if (owned) wear(if (worn) null else cur.id) else confirm = cur })
        }
        y += 56f * d
        val cols = 4; val gap = 12f * d; val tw = (w - 28f * d - gap * (cols - 1)) / cols; val th = tw * 1.05f
        for ((i, it) in ITEMS.withIndex()) {
            val r = RectF(14f * d + (i % cols) * (tw + gap), y + (i / cols) * (th + gap), 14f * d + (i % cols) * (tw + gap) + tw, y + (i / cols) * (th + gap) + th)
            val owned = a?.owned?.contains(it.id) == true
            tile(c, r, thumb(it), it.name, if (owned) (if (a?.eq == it.id) "WORN" else "OWNED") else "◆ $PRICE", sel == it.id, d) { sel = it.id }
        }
        return y + ((ITEMS.size + cols - 1) / cols) * (th + gap)
    }

    private fun drawOutfits(c: Canvas, w: Float, y0: Float, d: Float, a: Account?): Float {
        var y = y0; val cols = 4; val gap = 12f * d; val tw = (w - 28f * d - gap * (cols - 1)) / cols; val th = tw * 1.25f
        for ((i, ch) in Characters.ALL.withIndex()) {
            val r = RectF(14f * d + (i % cols) * (tw + gap), y, 14f * d + (i % cols) * (tw + gap) + tw, y + th)
            p.style = Paint.Style.FILL; p.color = 0xFF8A6845.toInt(); c.drawRect(r, p)
            PortraitRenderer.draw(c, ch, RectF(r.left, r.top, r.right, r.bottom - 34f * d), if (profile.char == ch.id) profile.hairStyle.ifEmpty { null } else null, if (profile.char == ch.id) profile.hair.ifEmpty { null } else null)
            p.color = if (profile.char == ch.id) Ui.GOLD else 0xAA000000.toInt(); c.drawRect(r.left, r.bottom - 34f * d, r.right, r.bottom, p)
            p.typeface = Ui.FONT_BOLD; p.textSize = 12f * d; p.textAlign = Paint.Align.CENTER; p.color = if (profile.char == ch.id) Ui.INK else Color.WHITE
            c.drawText(ch.name, r.centerX(), r.bottom - 13f * d, p); p.textAlign = Paint.Align.LEFT
            if (profile.char == ch.id) { p.style = Paint.Style.STROKE; p.strokeWidth = 3f * d; p.color = Ui.GOLD; c.drawRect(r, p); p.style = Paint.Style.FILL }
            tiles.add(Pair(r) { profile.char = ch.id; onChanged?.invoke() })
        }
        y += th + 18f * d
        p.typeface = Ui.FONT_BOLD; p.textSize = 14f * d; p.color = Ui.GOLD; c.drawText("MY CLOTHES", 16f * d, y, p); y += 10f * d
        val mine = ITEMS.filter { a?.owned?.contains(it.id) == true }
        if (mine.isEmpty()) return note(c, "NO CLOTHES YET. BUY T-SHIRTS FROM THE STORE.", y, d)
        for ((i, it) in mine.withIndex()) {
            val r = RectF(14f * d + (i % cols) * (tw + gap), y + (i / cols) * (tw + gap), 14f * d + (i % cols) * (tw + gap) + tw, y + (i / cols) * (tw + gap) + tw)
            val worn = a?.eq == it.id
            tile(c, r, thumb(it), it.name, if (worn) "WORN" else "OWNED", worn, d) { wear(if (worn) null else it.id) }
        }
        return y + ((mine.size + cols - 1) / cols) * (tw + gap)
    }

    private fun wear(id: String?) { account?.eq = id; onChanged?.invoke(); onToast?.invoke(if (id == null) "CLOTHES REMOVED" else "WEARING " + ITEMS.first { it.id == id }.name); invalidate() }

    private fun drawConfirm(c: Canvas, w: Float, h: Float, d: Float, it: Item) {
        p.style = Paint.Style.FILL; p.color = 0x99000000.toInt(); c.drawRect(0f, 0f, w, h, p)
        val bw = 280f * d; val bh = 230f * d; val r = RectF(w / 2 - bw / 2, h / 2 - bh / 2, w / 2 + bw / 2, h / 2 + bh / 2)
        p.color = 0xFF14161D.toInt(); c.drawRect(r, p); p.color = Ui.GOLD; c.drawRect(r.left, r.top, r.left + 4f * d, r.bottom, p)
        p.typeface = Ui.FONT_BOLD; p.textAlign = Paint.Align.CENTER; p.textSize = 19f * d; p.color = Color.WHITE; c.drawText("BUY " + it.name + "?", r.centerX(), r.top + 28f * d, p)
        thumb(it)?.let { b -> val s = 100f * d / b.height; c.drawBitmap(b, null, RectF(r.centerX() - b.width * s / 2, r.top + 38f * d, r.centerX() + b.width * s / 2, r.top + 38f * d + 100f * d), p) }
        p.textSize = 18f * d; p.color = Ui.GOLD; c.drawText("◆ $PRICE", r.centerX(), r.top + 160f * d, p)
        yesR.set(r.left + 20f * d, r.bottom - 48f * d, r.centerX() - 6f * d, r.bottom - 12f * d); noR.set(r.centerX() + 6f * d, r.bottom - 48f * d, r.right - 20f * d, r.bottom - 12f * d)
        p.color = Ui.GOLD; c.drawRect(yesR, p); p.color = 0xFF333333.toInt(); c.drawRect(noR, p)
        p.textSize = 16f * d; p.color = Ui.INK; c.drawText("BUY", yesR.centerX(), yesR.centerY() + 6f * d, p); p.color = Color.WHITE; c.drawText("CANCEL", noR.centerX(), noR.centerY() + 6f * d, p)
        p.textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x; val y = e.y; val d = resources.displayMetrics.density
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { dragY = y; moved = false; dragX = x; inPanel = confirm == null && x > width * 0.70f }
            MotionEvent.ACTION_MOVE -> if (inPanel) { onRotate?.invoke(x - dragX); dragX = x } else if (confirm == null && (Math.abs(y - dragY) > 6f * d || moved)) { moved = true; scroll = (scroll - (y - dragY)).coerceIn(0f, maxScroll); dragY = y; invalidate() }
            MotionEvent.ACTION_UP -> {
                if (inPanel) { inPanel = false; return true }
                if (moved) return true
                val cf = confirm
                if (cf != null) {
                    if (yesR.contains(x, y)) {
                        val a = account
                        if (a != null) {
                            if (a.diamonds < PRICE) onToast?.invoke("NOT ENOUGH DIAMONDS")
                            else { a.diamonds -= PRICE; a.owned.add(cf.id); a.eq = cf.id; onChanged?.invoke(); onToast?.invoke("PURCHASED") }
                        }
                        confirm = null
                    } else if (noR.contains(x, y)) confirm = null
                    invalidate(); return true
                }
                if (backR.contains(x, y)) { onClose?.invoke(); return true }
                for (t in tiles.reversed()) if (t.first.contains(x, y)) { t.second(); invalidate(); break }
            }
        }
        return true
    }
}
