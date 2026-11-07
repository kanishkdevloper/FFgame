package com.dustline.game

import android.content.Context
import android.opengl.GLES30 as GL
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sin

/**
 * The 3D lobby avatar (#lobby scene of the HTML): the picked character, real rigged mesh + textures, standing in the lobby.
 * Drag left / right to turn it. The Canvas lobby UI (LobbyView) is drawn over this view.
 */
class LobbyAvatarView(ctx: Context) : GLSurfaceView(ctx) {
    @Volatile var charId = "survivor"
    /** STORE / VAULT: the avatar fills the right 30 % of the screen and shows `previewWear` (a top that is selected but not bought yet) instead of the worn one. */
    @Volatile var panel = false
    @Volatile var usePreview = false
    @Volatile var previewWear: String? = null
    @Volatile private var surfW = 1; @Volatile private var surfH = 1
    fun rotateBy(dx: Float) { spin += dx * 0.012f; idleFrom = System.nanoTime() }
    @Volatile private var spin = 0.35f
    private var lastX = 0f; private var dragging = false
    @Volatile private var idleFrom = 0L

    private val r = object : Renderer {
        private val chars = CharRenderer(ctx)
        private val pose = CharPose()
        private val proj = FloatArray(16); private val view = FloatArray(16); private val vp = FloatArray(16); private val model = FloatArray(16)
        private val t0 = System.nanoTime()

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            chars.init()
            GL.glClearColor(0.075f, 0.055f, 0.105f, 1f)
            GL.glEnable(GL.GL_DEPTH_TEST)
        }

        override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
            surfW = w; surfH = h
            GL.glViewport(0, 0, w, h)
            Matrix.perspectiveM(proj, 0, 28f, w.toFloat() / h, 0.1f, 60f)
        }

        override fun onDrawFrame(gl: GL10?) {
            GL.glClear(GL.GL_COLOR_BUFFER_BIT or GL.GL_DEPTH_BUFFER_BIT)
            if (!chars.ready || !chars.has(charId)) return
            val sw = surfW; val sh = surfH
            if (panel) { val pw = (sw * 0.30f).toInt().coerceAtLeast(1); GL.glViewport(sw - pw, 0, pw, sh); Matrix.perspectiveM(proj, 0, 28f, pw.toFloat() / sh, 0.1f, 60f) }
            else { GL.glViewport(0, 0, sw, sh); Matrix.perspectiveM(proj, 0, 28f, sw.toFloat() / sh, 0.1f, 60f) }
            val t = (System.nanoTime() - t0) / 1e9f
            if (!dragging && System.nanoTime() - idleFrom > 2_500_000_000L) spin += 0.004f   // slow turntable when not touched
            Matrix.setLookAtM(view, 0, 0f, 1.25f, 6.6f, 0f, 1.0f, 0f, 0f, 1f, 0f)
            Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, 0f, 0.03f * sin(t * 1.6f), 0f)
            Matrix.rotateM(model, 0, Math.toDegrees(spin.toDouble()).toFloat(), 0f, 1f, 0f)
            Matrix.scaleM(model, 0, 1.18f, 1.18f, 1.18f)
            Matrix.translateM(model, 0, 0f, -0.88f, 0f)
            pose.mode = CharPose.RELAXED; pose.speed = 0f; pose.walk = 0f; pose.sprint = false; pose.crouch = 0f
            chars.draw(charId, vp, model, 0f, 1.25f, 6.6f, 8f, pose, wear = if (usePreview) previewWear else Wear.id)
        }
    }

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        setRenderer(r)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { dragging = true; lastX = e.x }
            MotionEvent.ACTION_MOVE -> { spin += (e.x - lastX) * 0.012f; lastX = e.x }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dragging = false; idleFrom = System.nanoTime() }
        }
        return true
    }
}
