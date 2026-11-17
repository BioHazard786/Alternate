package com.lulu786.alternate.callerid

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle

/**
 * Avatar for the call popup: the contact's own Material shape with the same soft gradient the app uses.
 * [progress] morphs circle -> shape; a spring that overshoots 1 gives the expressive "pop".
 * Children (initial / photo) are clipped to the morphing outline.
 */
class ShapedAvatarView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private var morph: Morph? = null
    private var bounds = floatArrayOf(0f, 0f, 1f, 1f)
    private val path = Path()
    private val matrix = Matrix()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    var progress = 1f
        set(value) { field = value; invalidate() }

    init { setWillNotDraw(false) }

    fun setShape(polygon: RoundedPolygon, container: Int, onContainer: Int) {
        morph = Morph(RoundedPolygon.circle(radius = 0.5f, centerX = 0.5f, centerY = 0.5f), polygon).also { bounds = it.calculateMaxBounds() }
        fill.color = container
        gradient = ColorUtils.blendARGB(container, 0xFFFFFFFF.toInt(), 0.22f) to ColorUtils.blendARGB(container, onContainer, 0.16f)
        rebuildShaders()
        invalidate()
    }

    private var gradient: Pair<Int, Int>? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); rebuildShaders() }

    private fun rebuildShaders() {
        val g = gradient ?: return
        val s = width.toFloat().coerceAtLeast(1f)
        fill.shader = LinearGradient(0f, 0f, s, s, g.first, g.second, Shader.TileMode.CLAMP)
        glow.shader = RadialGradient(s * 0.3f, s * 0.25f, s * 0.75f, 0x47FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
    }

    private fun buildPath() {
        val m = morph ?: return
        path.rewind()
        var first = true
        for (cubic in m.asCubics(progress)) {
            if (first) { path.moveTo(cubic.anchor0X, cubic.anchor0Y); first = false }
            path.cubicTo(cubic.control0X, cubic.control0Y, cubic.control1X, cubic.control1Y, cubic.anchor1X, cubic.anchor1Y)
        }
        path.close()
        val bw = bounds[2] - bounds[0]; val bh = bounds[3] - bounds[1]
        matrix.setScale(width / bw, height / bh)
        matrix.preTranslate(-bounds[0], -bounds[1])
        path.transform(matrix)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (morph == null) return super.dispatchDraw(canvas)
        buildPath()
        canvas.save()
        canvas.clipPath(path)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glow)
        super.dispatchDraw(canvas)
        canvas.restore()
    }
}
