package dev.jarvis.android.ui

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.sin

class OrbView(context: Context) : View(context) {
    var accent = Color.rgb(101, 220, 212)
        set(value) { field = value; rebuildGlow(); invalidate() }
    var active = false
    var level = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var glow: Shader? = null
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w,h,oldw,oldh); rebuildGlow() }
    private fun rebuildGlow() {
        val radius = minOf(width,height) * .32f * 1.65f
        if (radius > 0) glow = RadialGradient(width/2f,height/2f,radius,intArrayOf(withAlpha(accent,65),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f; val radius = minOf(width, height) * .32f
        val wave = if (active) ((sin(android.os.SystemClock.uptimeMillis() / 650.0) + 1) / 2).toFloat() else .15f
        paint.style = Paint.Style.FILL
        paint.shader = glow
        canvas.drawCircle(cx, cy, radius * 1.65f, paint); paint.shader = null
        paint.color = Color.rgb(15, 30, 40); canvas.drawCircle(cx, cy, radius, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = resources.displayMetrics.density * 1.2f
        paint.color = withAlpha(accent, 70); canvas.drawCircle(cx, cy, radius * (1.08f + .03f * wave), paint)
        paint.strokeWidth = resources.displayMetrics.density * 2.0f; paint.color = withAlpha(accent, if (active) 240 else 120)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.strokeCap = Paint.Cap.ROUND
        val unit = radius * .20f
        for (i in -2..2) {
            val h = radius * (.22f + .24f * (1f - kotlin.math.abs(i) / 3f) + level * .5f * if (i % 2 == 0) 1f else .6f)
            canvas.drawLine(cx + i * unit, cy - h / 2, cx + i * unit, cy + h / 2, paint)
        }
        paint.strokeCap = Paint.Cap.BUTT
        if (active && isAttachedToWindow) postInvalidateDelayed(50)
    }
    private fun withAlpha(color: Int, alpha: Int) = (color and 0xffffff) or (alpha shl 24)
}
