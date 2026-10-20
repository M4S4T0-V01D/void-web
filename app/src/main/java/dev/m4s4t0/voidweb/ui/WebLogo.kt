package dev.m4s4t0.voidweb.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The Void Web logo, drawn live: same geometry as art/gen_icon.py (108-unit grid). A deep-space globe with the
 * VOID spiral inside and a flame-fox curled around it. [spin] rotates the arms (degrees), [reach] grows them out
 * of the spark (0..1), [glow] scales their halo, [wrap] (0..1) draws the fox from its snout round to its tail,
 * [globe] (0..1) fades the globe in.
 */
@Composable
fun WebLogo(modifier: Modifier = Modifier, spin: Float = 0f, reach: Float = 1f, glow: Float = 1f, wrap: Float = 1f, globe: Float = 1f, palette: Palette = Void.palette) {
    val colors = LogoColors.from(palette)
    Canvas(modifier) {
        val s = min(size.width, size.height) / 108f
        translate((size.width - 108f * s) / 2f, (size.height - 108f * s) / 2f) {
            scale(s, s, pivot = Offset.Zero) { drawLogo(colors, spin, reach, glow, wrap, globe) }
        }
    }
}

/** The logo's colours for a theme; the same mapping as palette() in art/gen_icon.py, so it matches the app icon. */
private class LogoColors(val fox: List<Pair<Float, Color>>, val foxGlow: Color, val arms: List<Color>, val hot: Color, val core: List<Color>, val rim: Color, val glow: Color, val sparkEnd: Color) {
    companion object {
        private val DEEP = Color(0xFF0B0820)

        fun from(p: Palette): LogoColors {
            val hot = lerp(Color.White, p.accent2, 0.05f)
            return LogoColors(
                fox = listOf(0f to lerp(p.accent, p.accent2, 0.35f), 0.4f to p.accent, 0.72f to lerp(p.accent, p.accent2, 0.55f), 0.9f to p.accent2, 1f to lerp(hot, p.accent2, 0.25f)),
                foxGlow = lerp(p.accent, p.accent2, 0.4f),
                arms = listOf(p.accent2, lerp(p.accent, Color.White, 0.25f)),
                hot = hot,
                core = listOf(lerp(DEEP, p.accent, 0.3f), DEEP, Color(0xFF05040C)),
                rim = lerp(p.accent2, p.accent, 0.3f),
                glow = p.accent2,
                sparkEnd = lerp(Color.White, p.accent2, 0.35f),
            )
        }
    }
}

private const val CX = 54f
private const val CY = 54f
private const val GLOBE_R = 20f
private const val THETA_MAX = 1.7 * PI
private const val ARM_K = 0.49
private const val ARM_W = 0.85
private const val SPARK_S = 0.8f

// The fox (see art/gen_icon.py): its inner edge lies on the globe, t runs from the snout (0) to the tail tip (1).
private const val FOX_TH0 = -110.0 * PI / 180
private const val FOX_SWEEP = 306.0 * PI / 180
private const val FOX_IN = GLOBE_R - 1.2
private val FOX_W = listOf(0.0 to 0.0, 0.025 to 2.0, 0.05 to 4.4, 0.08 to 7.4, 0.13 to 7.8, 0.18 to 6.9, 0.3 to 9.0, 0.45 to 11.2, 0.62 to 10.6, 0.76 to 9.4, 0.88 to 6.4, 0.95 to 3.0, 1.0 to 0.0)
private const val EAR_A = 0.075
private const val EAR_TIP = 0.102
private const val EAR_B = 0.124
private const val EAR_H = 5.8
private const val EAR_LEAN = 0.75
private const val SNOUT_LIFT = 3.2

private fun smoothstep(e0: Double, e1: Double, x: Double): Double {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}

private fun interp(t: Double): Double {
    for (i in 0 until FOX_W.lastIndex) {
        val (t0, v0) = FOX_W[i]
        val (t1, v1) = FOX_W[i + 1]
        if (t <= t1) {
            val f = if (t1 > t0) (1 - cos(PI * (t - t0) / (t1 - t0))) / 2 else 0.0
            return v0 + (v1 - v0) * f
        }
    }
    return FOX_W.last().second
}

private fun ear(t: Double): Double {
    if (t <= EAR_A || t >= EAR_B) return 0.0
    val f = if (t < EAR_TIP) (t - EAR_A) / (EAR_TIP - EAR_A) else (EAR_B - t) / (EAR_B - EAR_TIP)
    return EAR_H * f.pow(1.5)
}

/** The fox outline from the snout to [upTo] (for the opening animation, where it wraps round the globe). */
private fun foxPath(upTo: Float, n: Int = 240): Path {
    val outer = ArrayList<Offset>(n + 1)
    val inner = ArrayList<Offset>(n + 1)
    val end = upTo.toDouble().coerceIn(0.0, 1.0)
    for (i in 0..n) {
        val t = end * i / n
        val th = FOX_TH0 - t * FOX_SWEEP
        val inn = FOX_IN + SNOUT_LIFT * (1 - smoothstep(0.0, 0.075, t)) - 5.0 * smoothstep(0.7, 1.0, t)
        val e = ear(t)
        val out = inn + interp(t) + e
        val c = cos(th)
        val s = sin(th)
        val lean = e * EAR_LEAN
        outer += Offset((CX + out * c - lean * s).toFloat(), (CY + out * s + lean * c).toFloat())
        inner += Offset((CX + inn * c).toFloat(), (CY + inn * s).toFloat())
    }
    return Path().apply {
        moveTo(outer[0].x, outer[0].y)
        for (p in outer.drop(1)) lineTo(p.x, p.y)
        for (p in inner.asReversed()) lineTo(p.x, p.y)
        close()
    }
}

private val FOX = foxPath(1f)

private fun gradientAt(stops: List<Pair<Float, Color>>, t: Float): Color {
    for (i in 0 until stops.lastIndex) {
        val (t0, c0) = stops[i]
        val (t1, c1) = stops[i + 1]
        if (t <= t1) return lerp(c0, c1, (t - t0) / (t1 - t0))
    }
    return stops.last().second
}

/** The fox's colours as a sweep gradient round the centre (0 = three o'clock, clockwise), like fox_sweep(). */
private fun foxBrush(stops: List<Pair<Float, Color>>): Brush {
    val th0 = Math.toDegrees(FOX_TH0)
    val sweep = Math.toDegrees(FOX_SWEEP)
    val list = ArrayList<Pair<Float, Color>>()
    for (i in 0..50) {
        val t = i / 50f
        list += (((th0 - t * sweep) % 360 + 360) % 360 / 360).toFloat() to gradientAt(stops, t)
    }
    val wrap = (((th0 % 360) + 360) % 360 / sweep).toFloat()
    list += 0f to gradientAt(stops, wrap)
    list += 1f to gradientAt(stops, wrap)
    return Brush.sweepGradient(*list.sortedBy { it.first }.toTypedArray(), center = Offset(CX, CY))
}

private fun armPath(phase: Double, t0: Float, t1: Float, widthScale: Float, n: Int = 24): Path {
    val pts = (0..n).map { i ->
        val t = t0 + (t1 - t0) * i / n
        val th = t * THETA_MAX
        val r = (2.5 + 36.0 * t.toDouble().pow(1.25)) * ARM_K
        Triple((CX + r * cos(th + phase)).toFloat(), (CY + r * sin(th + phase)).toFloat(), t)
    }
    val left = ArrayList<Offset>(n + 1)
    val right = ArrayList<Offset>(n + 1)
    for (i in 0..n) {
        val (x, y, t) = pts[i]
        val a = pts[max(i - 1, 0)]
        val b = pts[min(i + 1, n)]
        val dx = b.first - a.first
        val dy = b.second - a.second
        val d = hypot(dx, dy).takeIf { it > 0f } ?: 1f
        val w = ((0.3 + 2.5 * sin(PI * min(t, 0.999f).toDouble().pow(0.55))) * widthScale * ARM_W / 2).toFloat()
        left += Offset(x - dy / d * w, y + dx / d * w)
        right += Offset(x + dy / d * w, y - dx / d * w)
    }
    return Path().apply {
        moveTo(left[0].x, left[0].y)
        for (p in left.drop(1)) lineTo(p.x, p.y)
        for (p in right.asReversed()) lineTo(p.x, p.y)
        close()
    }
}

private fun sparkPath(): Path {
    fun p(dx: Float, dy: Float) = Offset(CX + dx * SPARK_S, CY + dy * SPARK_S)
    return Path().apply {
        p(0f, -9.5f).let { moveTo(it.x, it.y) }
        val segs = listOf(
            listOf(p(0.9f, -3.4f), p(3.4f, -0.9f), p(9.5f, 0f)),
            listOf(p(3.4f, 0.9f), p(0.9f, 3.4f), p(0f, 9.5f)),
            listOf(p(-0.9f, 3.4f), p(-3.4f, 0.9f), p(-9.5f, 0f)),
            listOf(p(-3.4f, -0.9f), p(-0.9f, -3.4f), p(0f, -9.5f)),
        )
        for ((c1, c2, e) in segs) cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
        close()
    }
}

private val SPARK = sparkPath()

private fun DrawScope.drawLogo(colors: LogoColors, spin: Float, reach: Float, glow: Float, wrap: Float, globe: Float) {
    val center = Offset(CX, CY)
    val g = globe.coerceIn(0f, 1f)
    val w = wrap.coerceIn(0f, 1f)
    // The fox's aura, then the globe: deep space with faint meridians and a thin rim.
    val auraR = GLOBE_R + 13f
    if (w > 0f) drawCircle(Brush.radialGradient(0.55f to colors.foxGlow.copy(alpha = 0.32f * w), 1f to colors.foxGlow.copy(alpha = 0f), center = center, radius = auraR), auraR, center)
    if (g > 0f) {
        drawCircle(Brush.radialGradient(colors.core, center, GLOBE_R), GLOBE_R, center, alpha = g)
        val grid = Stroke(0.7f)
        drawOval(colors.rim, Offset(CX - GLOBE_R * 0.42f, CY - GLOBE_R), androidx.compose.ui.geometry.Size(GLOBE_R * 0.84f, GLOBE_R * 2), alpha = 0.16f * g, style = grid)
        drawOval(colors.rim, Offset(CX - GLOBE_R, CY - GLOBE_R * 0.36f), androidx.compose.ui.geometry.Size(GLOBE_R * 2, GLOBE_R * 0.72f), alpha = 0.16f * g, style = grid)
        drawCircle(colors.rim, GLOBE_R, center, alpha = 0.55f * g, style = Stroke(1.1f))
    }
    val length = reach.coerceIn(0.02f, 1f)
    rotate(spin, pivot = center) {
        for ((phase, color) in listOf(-PI / 2 to colors.arms[0], PI / 2 to colors.arms[1])) {
            drawPath(armPath(phase, 0f, length, 4.2f * glow), color, alpha = (0.09f * glow).coerceAtMost(0.3f))
            drawPath(armPath(phase, 0f, length, 2.4f * glow), color, alpha = (0.26f * glow).coerceAtMost(0.6f))
            val segments = 14
            for (k in 0 until segments) {
                val t0 = k / segments.toFloat() * length
                val t1 = min(length, (k + 1.35f) / segments * length)
                val tm = (t0 + t1) / 2
                val c = lerp(colors.hot, color, min(1f, tm / 0.22f))
                val a = if (tm < 0.55f) 1f else 1f - 0.5f * (tm - 0.55f) / 0.45f
                drawPath(armPath(phase, t0, t1, 1f, 8), c, alpha = a)
            }
        }
    }
    val gr = 10f * max(glow, 0.6f)
    drawCircle(Brush.radialGradient(listOf(colors.glow.copy(alpha = 0.6f * min(glow, 1.5f) / 1.5f + 0.2f), colors.glow.copy(alpha = 0f)), center, gr), gr, center)
    rotate(spin * 0.25f, pivot = center) {
        drawPath(SPARK, Brush.radialGradient(listOf(Color.White, colors.sparkEnd), center, 9.5f * SPARK_S))
    }
    if (w > 0f) drawPath(if (w >= 1f) FOX else foxPath(w, 120), foxBrush(colors.fox))
}
