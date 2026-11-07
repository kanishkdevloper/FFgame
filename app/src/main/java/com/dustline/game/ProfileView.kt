package com.dustline.game

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Native port of the HTML profile screen (renderProfile / PF_MENU):
 * GALLERY (badges + days), OVERVIEW > STATS / PERFORMANCE / COINS, ROLE > CHARACTER / OUTFIT, HISTORY, FRIENDS, ALBUM, HONOR SCORE, WISHLIST.
 * Top card: LV + XP bar, level reward gift, name (gear = change name), language, UID + copy. Side buttons: photo, role, profile code, share, next, edit.
 */
class ProfileView(ctx: Context) : View(ctx) {
    var account: Account? = null
    var profile: Profile = Profile()
    var onClose: (() -> Unit)? = null
    var onEditName: (() -> Unit)? = null
    var onSaved: (() -> Unit)? = null

    private val menu = listOf(
        "GALLERY" to emptyList<String>(), "OVERVIEW" to listOf("STATS", "PERFORMANCE", "COINS"), "ROLE" to listOf("CHARACTER", "OUTFIT"),
        "HISTORY" to emptyList(), "FRIENDS" to emptyList(), "ALBUM" to emptyList(), "HONOR SCORE" to emptyList(), "WISHLIST" to emptyList()
    )
    private var tab = 0; private var openIdx = -1; private var sub = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val closeR = RectF(); private val gearR = RectF(); private val copyR = RectF(); private val giftR = RectF()
    private val camR = RectF(); private val avR = RectF(); private val qrR = RectF(); private val shareR = RectF(); private val nextR = RectF(); private val editR = RectF()
    private val i1R = RectF(); private val i2R = RectF(); private val i3R = RectF()
    private val menuR = ArrayList<Pair<RectF, IntArray>>()          // rect -> [tab, sub(-1 = none)]
    private val heartR = ArrayList<Pair<RectF, String>>()
    private val photoR = ArrayList<Pair<RectF, File>>()
    private var menuScroll = 0f; private var lowScroll = 0f; private var lastY = 0f; private var dragMenu = false; private var moved = false
    private var toast = ""; private var toastUntil = 0L
    private val h = Handler(Looper.getMainLooper())
    private val album: File get() = File(context.filesDir, "album").also { it.mkdirs() }

    fun open() { tab = 0; openIdx = -1; sub = 0; menuScroll = 0f; lowScroll = 0f; visibility = VISIBLE; invalidate() }

    private fun say(t: String) { toast = t; toastUntil = System.currentTimeMillis() + 2200; invalidate(); h.postDelayed({ invalidate() }, 2300) }
    private fun fmtTime(sec: Long): String { val s = maxOf(0L, sec); val hh = s / 3600; val mm = s % 3600 / 60; return if (hh > 0) "${hh}H ${mm}M" else "${mm}M ${s % 60}S" }
    private fun days(a: Account) = maxOf(0L, (System.currentTimeMillis() - a.created) / 86_400_000L)
    private fun copy(text: String, msg: String) {
        try { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("dustline", text)); say(msg) } catch (e: Exception) { say("COPY FAILED") }
    }
    private fun share(text: String) {
        try { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share profile").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { copy(text, "PROFILE COPIED") }
    }
    private fun photos(): List<File> = (album.listFiles()?.filter { it.extension == "jpg" }?.sortedByDescending { it.lastModified() } ?: emptyList())

    private fun takePhoto() {
        try {
            val w = width; val hh = height; if (w <= 0 || hh <= 0) return
            val full = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888); draw(Canvas(full))
            val sw = minOf(640, w); val sh = hh * sw / w
            val sc = Bitmap.createScaledBitmap(full, sw, sh, true)
            File(album, "p${System.currentTimeMillis()}.jpg").outputStream().use { sc.compress(Bitmap.CompressFormat.JPEG, 72, it) }
            photos().drop(12).forEach { it.delete() }
            say("PHOTO SAVED TO ALBUM"); if (tab == 5) invalidate()
        } catch (e: Exception) { say("PHOTO FAILED") }
    }

    private fun lang(): String = try { Locale.getDefault().getDisplayLanguage(Locale.ENGLISH).uppercase() } catch (e: Exception) { "ENGLISH" }

    private fun txt(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = true, al: Paint.Align = Paint.Align.LEFT) {
        p.typeface = if (bold) Ui.FONT_BOLD else Ui.FONT_MED; p.textSize = size; p.color = color; p.textAlign = al; c.drawText(s, x, y, p)
    }
    private fun btn(c: Canvas, r: RectF, label: String, d: Float, on: Boolean = false) {
        p.style = Paint.Style.FILL; p.color = if (on) Ui.GOLD else 0xFF241B33.toInt(); c.drawRect(r, p)
        txt(c, label, r.centerX(), r.centerY() + 5f * d, 13f * d, if (on) Ui.INK else Color.WHITE, true, Paint.Align.CENTER)
    }

    override fun onDraw(c: Canvas) {
        val a = account ?: return
        val w = width.toFloat(); val hh = height.toFloat(); val d = resources.displayMetrics.density
        c.drawColor(0xF4100C18.toInt()); p.style = Paint.Style.FILL
        p.color = Ui.GOLD; c.drawRect(0f, 0f, w, 4f * d, p)
        txt(c, "PROFILE", 20f * d, 38f * d, 22f * d, Color.WHITE)
        closeR.set(w - 56f * d, 8f * d, w - 12f * d, 52f * d); btn(c, closeR, "X", d, true)

        // ---- left menu
        menuR.clear(); val mx = 12f * d; val mw = 150f * d; var my = 64f * d - menuScroll
        val save = c.save(); c.clipRect(mx, 60f * d, mx + mw, hh)
        for ((i, m) in menu.withIndex()) {
            val r = RectF(mx, my, mx + mw, my + 38f * d); menuR.add(r to intArrayOf(i, -1))
            btn(c, r, m.first + (if (m.second.isNotEmpty()) (if (openIdx == i) "  \u25B4" else "  \u25BE") else ""), d, tab == i && openIdx != i); my += 42f * d
            if (openIdx == i) for ((j, n) in m.second.withIndex()) {
                val sr = RectF(mx + 14f * d, my, mx + mw, my + 32f * d); menuR.add(sr to intArrayOf(i, j))
                btn(c, sr, n, d, tab == i && sub == j); my += 36f * d
            }
        }
        c.restoreToCount(save)

        // ---- card
        val cx = mx + mw + 16f * d; val cw = w - cx - 70f * d; val ch = 150f * d; val cy = 64f * d
        p.shader = LinearGradient(cx, cy, cx + cw, cy + ch, 0xFF3A1A12.toInt(), 0xFF161022.toInt(), Shader.TileMode.CLAMP); c.drawRect(cx, cy, cx + cw, cy + ch, p); p.shader = null
        txt(c, "DUSTLINE", cx + 12f * d, cy + 24f * d, 15f * d, Ui.GOLD)
        gearR.set(cx + cw - 40f * d, cy + 6f * d, cx + cw - 6f * d, cy + 40f * d); btn(c, gearR, "\u270E", d)
        // avatar square
        val av = RectF(cx + 12f * d, cy + 36f * d, cx + 12f * d + 74f * d, cy + 36f * d + 74f * d)
        p.color = 0xFFA01C12.toInt(); c.drawRect(av, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 7f * d; p.color = 0xFFFFB21A.toInt(); c.drawLine(av.left, av.top, av.right, av.bottom, p); c.drawLine(av.right, av.top, av.left, av.bottom, p); p.style = Paint.Style.FILL
        txt(c, "LV.${a.level}", av.right + 14f * d, cy + 56f * d, 18f * d, Color.WHITE)
        giftR.set(av.right + 80f * d, cy + 38f * d, av.right + 112f * d, cy + 66f * d); btn(c, giftR, "\uD83C\uDF81", d)
        p.color = 0x33FFFFFF; val bx = av.right + 14f * d; val bw = cw - (bx - cx) - 16f * d; c.drawRect(bx, cy + 70f * d, bx + bw, cy + 76f * d, p)
        p.color = Ui.GOLD; c.drawRect(bx, cy + 70f * d, bx + bw * a.progress, cy + 76f * d, p)
        txt(c, a.name, bx, cy + 98f * d, 20f * d, Color.WHITE); txt(c, lang(), bx, cy + 114f * d, 11f * d, 0xFFBBBBBB.toInt(), false)
        txt(c, "UID: ${a.uid}", bx, cy + 136f * d, 12f * d, Ui.GOLD, false)
        val ts = d * 28f
        copyR.set(bx + 130f * d, cy + 118f * d, bx + 130f * d + ts, cy + 118f * d + ts * 0.85f); btn(c, copyR, "\u2398", d)
        i1R.set(bx + 190f * d, cy + 112f * d, bx + 190f * d + ts, cy + 112f * d + ts); btn(c, i1R, "\uD83D\uDCC5", d)
        i2R.set(i1R.right + 6f * d, i1R.top, i1R.right + 6f * d + ts, i1R.bottom); btn(c, i2R, "\u2600", d)
        i3R.set(i2R.right + 6f * d, i1R.top, i2R.right + 6f * d + ts, i1R.bottom); btn(c, i3R, "\u2708", d)
        txt(c, "\u2665 ${a.likes}", i3R.right + 12f * d, i1R.centerY() + 5f * d, 13f * d, 0xFFFF6A7A.toInt())
        txt(c, "\u21C4 SOLO BATTLE ROYALE", cx, cy + ch + 22f * d, 13f * d, Color.WHITE)

        // ---- side tools (right edge)
        val sx = w - 56f * d; var sy = cy + 4f * d
        for ((r, l) in listOf(camR to "\uD83D\uDCF7", avR to "\uD83D\uDC64", qrR to "\u25A3", shareR to "\u21EA")) { r.set(sx, sy, sx + 44f * d, sy + 44f * d); btn(c, r, l, d); sy += 52f * d }
        nextR.set(sx, sy + 8f * d, sx + 44f * d, sy + 52f * d); btn(c, nextR, "\u00BB", d)
        editR.set(sx, sy + 60f * d, sx + 44f * d, sy + 104f * d); btn(c, editR, "\u270E", d)

        // ---- lower panel
        val ly = cy + ch + 34f * d; val lh = hh - ly - 10f * d
        p.color = 0x66000000; c.drawRect(cx, ly, cx + cw, ly + lh, p)
        val sv = c.save(); c.clipRect(cx, ly, cx + cw, ly + lh)
        heartR.clear(); photoR.clear()
        val contentH = drawLower(c, a, cx + 12f * d, ly + 8f * d - lowScroll, cw - 24f * d, d)
        c.restoreToCount(sv)
        maxLow = maxOf(0f, contentH + 16f * d - lh); lowScroll = lowScroll.coerceIn(0f, maxLow)

        if (System.currentTimeMillis() < toastUntil) {
            p.color = 0xE6000000.toInt(); val tw = p.measureText(toast).coerceAtLeast(10f)
            c.drawRect(w / 2 - 190f * d, hh - 56f * d, w / 2 + 190f * d, hh - 18f * d, p); txt(c, toast, w / 2, hh - 31f * d, 14f * d, Ui.GOLD, true, Paint.Align.CENTER)
        }
    }
    private var maxLow = 0f

    /** returns the drawn content height */
    private fun drawLower(c: Canvas, a: Account, x: Float, y0: Float, w: Float, d: Float): Float {
        var y = y0
        fun row(l: String, v: String) {
            y += 26f * d; txt(c, l, x, y, 13f * d, 0xFFCCCCCC.toInt(), false); txt(c, v, x + w, y, 14f * d, Color.WHITE, true, Paint.Align.RIGHT)
            p.color = 0x22FFFFFF; c.drawRect(x, y + 6f * d, x + w, y + 7f * d, p)
        }
        fun empty(s: String) { y += 30f * d; txt(c, s, x, y, 13f * d, 0xFF999999.toInt(), false) }
        val st = a
        val mt = maxOf(1, st.matches); val deaths = maxOf(1, st.matches - st.wins)
        val next = if (a.level < XpTable.TABLE.size) XpTable.TABLE[a.level] else 0L
        when {
            tab == 0 -> {
                val bd = ArrayList<String>()
                if (st.matches >= 1) bd.add("FIRST MATCH"); if (st.wins >= 1) bd.add("FIRST WIN"); if (st.wins >= 10) bd.add("10 WINS")
                if (st.kills >= 100) bd.add("100 ELIMINATIONS"); if (a.level >= 10) bd.add("LEVEL 10"); if (a.level >= 25) bd.add("LEVEL 25"); if (days(a) >= 365) bd.add("1 YEAR OLD")
                if (bd.isEmpty()) empty("PLAY MATCHES TO EARN BADGES") else {
                    var bx = x; y += 8f * d
                    for (b in bd) {
                        p.typeface = Ui.FONT_BOLD; p.textSize = 12f * d; val bw = p.measureText(b) + 20f * d
                        if (bx + bw > x + w) { bx = x; y += 34f * d }
                        p.color = Ui.GOLD; c.drawRect(bx, y, bx + bw, y + 26f * d, p); txt(c, b, bx + 10f * d, y + 18f * d, 12f * d, Ui.INK); bx += bw + 8f * d
                    }
                    y += 30f * d
                }
                y += 14f * d; txt(c, "${days(a)} Day(s)", x, y + 18f * d, 20f * d, Color.WHITE); txt(c, "WITH DUSTLINE", x, y + 36f * d, 11f * d, 0xFF999999.toInt(), false); y += 40f * d
            }
            tab == 1 && sub == 0 -> {
                row("LEVEL", a.level.toString()); row("TOTAL XP", "${a.xp}" + (if (next > 0) " / $next" else " (MAX)")); row("MATCHES PLAYED", st.matches.toString())
                row("WINS", st.wins.toString()); row("TOP 10 FINISHES", st.top10.toString()); row("ELIMINATIONS", st.kills.toString())
                row("BEST ELIMINATIONS IN A MATCH", st.bestKills.toString()); row("TIME PLAYED", fmtTime(st.timeSec)); row("LIKES", st.likes.toString())
            }
            tab == 1 && sub == 1 -> {
                val hs = st.history
                row("WIN RATE", "${Math.round(st.wins * 100f / mt)}%"); row("TOP 10 RATE", "${Math.round(st.top10 * 100f / mt)}%")
                row("K/D", String.format(Locale.US, "%.2f", st.kills.toFloat() / deaths)); row("AVG ELIMS / MATCH", String.format(Locale.US, "%.1f", st.kills.toFloat() / mt))
                row("AVG SURVIVAL", fmtTime(if (st.matches > 0) st.timeSec / st.matches else 0)); row("AVG XP / MATCH (LAST ${hs.size})", (if (hs.isEmpty()) 0 else hs.sumOf { it.xp } / hs.size).toString())
            }
            tab == 1 -> {
                val hs = st.history
                row("COINS", a.coins.toString()); row("DIAMONDS", a.diamonds.toString()); row("EARNED (LAST ${hs.size} MATCHES)", hs.sumOf { it.c }.toString())
                row("BEST MATCH", (hs.maxOfOrNull { it.c } ?: 0).toString()); row("FORMULA", "30 + MIN x3 + ELIM x10 + TOP3 60 + WIN 100 + PERF")
            }
            tab == 2 && sub == 0 -> {
                val ch = Characters.ALL.firstOrNull { it.id == profile.char } ?: Characters.ALL[0]
                row("CHARACTER", ch.name); row("HAIR STYLE", profile.hairStyle.ifEmpty { "-" }.uppercase()); row("HEADWEAR", "NONE"); row("CHARACTERS AVAILABLE", Characters.ALL.size.toString())
            }
            tab == 2 -> {
                row("JACKET / SHIRT", Wear.id?.uppercase() ?: "NONE"); row("CHEST RIG", "NONE"); row("TROUSERS", "NONE"); row("FOOTWEAR", "NONE")
                row("BACKPACK", "NONE"); row("HEADWEAR COLOR", "NONE"); row("HAIR", profile.hair.ifEmpty { "DEFAULT" }.uppercase()); row("SKIN", "DEFAULT")
            }
            tab == 3 -> {
                if (st.history.isEmpty()) empty("NO MATCHES YET") else {
                    val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    for (e in st.history) {
                        y += 26f * d; txt(c, df.format(Date(e.d)), x, y, 12f * d, 0xFFCCCCCC.toInt(), false)
                        txt(c, if (e.q) "LEFT EARLY" else if (e.w) "WINNER #1" else "#${e.p}", x + w * 0.38f, y, 13f * d, Color.WHITE)
                        txt(c, "${e.k} ELIM", x + w * 0.52f, y, 13f * d, Color.WHITE); txt(c, fmtTime(e.t.toLong()), x + w * 0.66f, y, 13f * d, Color.WHITE)
                        txt(c, "+${e.xp} XP / +${e.c} C", x + w, y, 13f * d, Ui.GOLD, true, Paint.Align.RIGHT)
                        p.color = 0x22FFFFFF; c.drawRect(x, y + 6f * d, x + w, y + 7f * d, p)
                    }
                }
            }
            tab == 4 -> empty("NO FRIENDS IN YOUR PARTY YET")
            tab == 5 -> {
                val ph = photos()
                if (ph.isEmpty()) empty("TAP THE CAMERA BUTTON TO SAVE A PHOTO") else {
                    val tw = 150f * d; val th = tw * height / maxOf(1, width); var px = x; y += 8f * d
                    for (f in ph) {
                        if (px + tw > x + w) { px = x; y += th + 8f * d }
                        val bmp = bitmaps.getOrPut(f.name) { BitmapFactory.decodeFile(f.path) }
                        if (bmp != null) c.drawBitmap(bmp, null, RectF(px, y, px + tw, y + th), null)
                        photoR.add(RectF(px, y, px + tw, y + th) to f); px += tw + 8f * d
                    }
                    y += th + 8f * d; txt(c, "TAP A PHOTO TO DELETE IT", x, y + 14f * d, 11f * d, 0xFF999999.toInt(), false); y += 20f * d
                }
            }
            tab == 6 -> {
                val q = st.quits; val ho = maxOf(0, 100 - q * 5)
                y += 44f * d; txt(c, ho.toString(), x, y, 40f * d, Ui.GOLD)
                row("RATING", if (ho >= 90) "EXCELLENT" else if (ho >= 70) "GOOD" else if (ho >= 50) "FAIR" else "POOR")
                row("MATCHES COMPLETED", maxOf(0, st.matches - q).toString()); row("LEFT EARLY (-5 EACH)", q.toString())
            }
            else -> {
                for (ch in Characters.ALL) {
                    y += 30f * d; txt(c, ch.name + (if (profile.char == ch.id) "  \u00B7 EQUIPPED" else ""), x, y, 13f * d, Color.WHITE)
                    val r = RectF(x + w - 36f * d, y - 22f * d, x + w, y + 6f * d); heartR.add(r to ch.id)
                    txt(c, "\u2665", r.centerX(), r.centerY() + 8f * d, 22f * d, if (a.wish.contains(ch.id)) 0xFFFF4A5A.toInt() else 0xFF777777.toInt(), true, Paint.Align.CENTER)
                    p.color = 0x22FFFFFF; c.drawRect(x, y + 8f * d, x + w, y + 9f * d, p)
                }
            }
        }
        return y - y0
    }
    private val bitmaps = HashMap<String, Bitmap?>()

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x; val y = e.y; val d = resources.displayMetrics.density
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastY = y; moved = false; dragMenu = x < 170f * d; return true }
            MotionEvent.ACTION_MOVE -> {
                val dy = lastY - y; if (kotlin.math.abs(dy) > 4f * d) moved = true
                if (moved) {
                    if (dragMenu) menuScroll = (menuScroll + dy).coerceIn(0f, 400f * d) else lowScroll = (lowScroll + dy).coerceIn(0f, maxLow)
                    lastY = y; invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> { if (!moved) tap(x, y); return true }
        }
        return true
    }

    private fun tap(x: Float, y: Float) {
        val a = account ?: return
        if (closeR.contains(x, y)) { onClose?.invoke(); return }
        if (gearR.contains(x, y) || editR.contains(x, y)) { onEditName?.invoke(); return }
        if (copyR.contains(x, y)) { copy(a.uid, "UID COPIED"); return }
        if (qrR.contains(x, y)) { copy("DUSTLINE | ${a.name} | UID ${a.uid}", "PROFILE CODE COPIED"); return }
        if (shareR.contains(x, y)) { share("DUSTLINE  |  ${a.name}  |  LV.${a.level}  |  UID ${a.uid}"); return }
        if (camR.contains(x, y)) { takePhoto(); return }
        if (avR.contains(x, y)) { tab = 2; openIdx = 2; sub = 0; lowScroll = 0f; invalidate(); return }
        if (nextR.contains(x, y)) { tab = (tab + 1) % menu.size; sub = 0; openIdx = if (menu[tab].second.isNotEmpty()) tab else -1; lowScroll = 0f; invalidate(); return }
        if (giftR.contains(x, y)) { say("LEVEL-UP REWARD: +100 COINS PER LEVEL"); return }
        if (i1R.contains(x, y)) { say("ACCOUNT CREATED " + DateFormat.getDateInstance().format(Date(a.created))); return }
        if (i2R.contains(x, y)) { say("TIME PLAYED " + fmtTime(a.timeSec)); return }
        if (i3R.contains(x, y)) { say("MATCHES PLAYED ${a.matches}"); return }
        for ((r, ij) in menuR) if (r.contains(x, y)) {
            val i = ij[0]; val j = ij[1]
            if (j >= 0) { tab = i; sub = j } else if (menu[i].second.isNotEmpty()) { if (openIdx == i) openIdx = -1 else { openIdx = i; tab = i; sub = 0 } } else { tab = i; openIdx = -1 }
            lowScroll = 0f; invalidate(); return
        }
        for ((r, id) in heartR) if (r.contains(x, y)) { if (a.wish.contains(id)) a.wish.remove(id) else a.wish.add(id); onSaved?.invoke(); invalidate(); return }
        for ((r, f) in photoR) if (r.contains(x, y)) {
            android.app.AlertDialog.Builder(context).setMessage("DELETE THIS PHOTO?").setPositiveButton("DELETE") { _, _ -> f.delete(); bitmaps.remove(f.name); invalidate() }.setNegativeButton("CANCEL", null).show(); return
        }
    }
}
