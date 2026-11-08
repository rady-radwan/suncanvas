package app.suncanvas.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

enum class SunGlyph { SUNRISE, SUNSET, SUN, MOON }

/** Hand-drawn sunrise / sunset glyphs matching the widget icons. */
@Composable
fun SunGlyphIcon(glyph: SunGlyph, color: Color, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        val cx = this.size.width / 2
        val cy = this.size.height / 2
        val stroke = r * 0.16f
        when (glyph) {
            SunGlyph.SUNRISE, SunGlyph.SUNSET -> {
                val horizonY = cy + r * 0.38f
                val sunR = r * 0.42f
                drawArc(color, 180f, 180f, true, Offset(cx - sunR, horizonY - sunR), Size(sunR * 2, sunR * 2))
                for (a in listOf(180.0, 225.0, 270.0, 315.0, 360.0)) {
                    val rad = Math.toRadians(a)
                    drawLine(color, Offset(cx + cos(rad).toFloat() * sunR * 1.35f, horizonY + sin(rad).toFloat() * sunR * 1.35f),
                        Offset(cx + cos(rad).toFloat() * sunR * 1.75f, horizonY + sin(rad).toFloat() * sunR * 1.75f), stroke, StrokeCap.Round)
                }
                drawLine(color, Offset(cx - r * 0.95f, horizonY), Offset(cx + r * 0.95f, horizonY), stroke, StrokeCap.Round)
                val ay = horizonY + r * 0.32f
                val rising = glyph == SunGlyph.SUNRISE
                val tipY = if (rising) ay - r * 0.04f else ay + r * 0.30f
                val baseY = tipY + (if (rising) 1 else -1) * r * 0.22f
                drawLine(color, Offset(cx - r * 0.2f, baseY), Offset(cx, tipY), stroke, StrokeCap.Round)
                drawLine(color, Offset(cx + r * 0.2f, baseY), Offset(cx, tipY), stroke, StrokeCap.Round)
            }
            SunGlyph.SUN -> {
                val sunR = r * 0.42f
                drawCircle(color, sunR, Offset(cx, cy))
                for (i in 0 until 8) {
                    val rad = Math.toRadians(i * 45.0)
                    drawLine(color, Offset(cx + cos(rad).toFloat() * sunR * 1.4f, cy + sin(rad).toFloat() * sunR * 1.4f),
                        Offset(cx + cos(rad).toFloat() * sunR * 1.95f, cy + sin(rad).toFloat() * sunR * 1.95f), stroke, StrokeCap.Round)
                }
            }
            SunGlyph.MOON -> drawMoon(Offset(cx, cy), r * 0.62f, color)
        }
    }
}
