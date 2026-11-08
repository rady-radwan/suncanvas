package app.suncanvas.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.suncanvas.ui.theme.LocalSunColors
import app.suncanvas.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).semantics { contentDescription = title },
        )
        action?.invoke()
    }
}

/** Rounded surface used for all cards: subtle container colour, no heavy shadows. */
@Composable
fun SunCard(modifier: Modifier = Modifier, contentPadding: Dp = Spacing.lg, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    supporting: String? = null,
) {
    SunCard(modifier = modifier, contentPadding = Spacing.md) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}

@Composable
fun DayNightDot(isDay: Boolean, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    val sun = LocalSunColors.current
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .semantics { contentDescription = if (isDay) "Daytime" else "Night-time" },
    ) {
        Canvas(Modifier.size(size)) {
            drawCircle(if (isDay) sun.sunrise else sun.blue)
        }
    }
}

/**
 * Sun path across the sky: dashed arc, travelled portion, horizon, Sun position (or a dim
 * below-horizon marker and the Moon at night).
 */
@Composable
fun SunArc(
    progress: Float?,
    nightProgress: Float?,
    modifier: Modifier = Modifier,
) {
    val sun = LocalSunColors.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val description = if (progress != null) "Sun is ${(progress * 100).toInt()} percent through today's daylight" else "The Sun is below the horizon"
    Canvas(modifier.semantics { contentDescription = description }) {
        val horizonY = size.height * 0.86f
        val rx = size.width / 2 * 0.9f
        val ry = min(size.height * 0.78f, rx)
        val cx = size.width / 2
        val topLeft = Offset(cx - rx, horizonY - ry)
        val arcSize = Size(rx * 2, ry * 2)
        drawLine(onSurface.copy(alpha = 0.25f), Offset(0f, horizonY), Offset(size.width, horizonY), strokeWidth = 1.5.dp.toPx())
        drawArc(
            color = sun.golden.copy(alpha = 0.45f), startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))),
        )
        if (progress != null) {
            drawArc(
                brush = Brush.horizontalGradient(listOf(sun.sunrise, sun.golden, sun.sunset)),
                startAngle = 180f, sweepAngle = 180f * progress, useCenter = false, topLeft = topLeft, size = arcSize,
                style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round),
            )
            val a = Math.toRadians(180.0 + 180.0 * progress)
            val p = Offset(cx + cos(a).toFloat() * rx, horizonY + sin(a).toFloat() * ry)
            drawCircle(Brush.radialGradient(listOf(sun.golden.copy(alpha = 0.55f), Color.Transparent), center = p, radius = 28.dp.toPx()), 28.dp.toPx(), p)
            drawCircle(sun.sunrise, 9.dp.toPx(), p)
            drawCircle(Color.White.copy(alpha = 0.9f), 4.dp.toPx(), p)
        } else {
            val np = nightProgress ?: 0.5f
            val a = Math.toRadians(180.0 - 180.0 * np)
            val p = Offset(cx + cos(a).toFloat() * rx, horizonY + sin(a).toFloat() * ry * 0.12f)
            drawCircle(sun.sunset.copy(alpha = 0.45f), 5.dp.toPx(), p)
            drawMoon(Offset(cx, horizonY - ry * 0.55f), 12.dp.toPx(), onSurface.copy(alpha = 0.85f))
        }
        // Horizon end markers.
        drawCircle(sun.sunrise, 3.dp.toPx(), Offset(cx - rx, horizonY))
        drawCircle(sun.sunset, 3.dp.toPx(), Offset(cx + rx, horizonY))
    }
}

fun DrawScope.drawMoon(center: Offset, r: Float, color: Color) {
    val moon = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
    val cut = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(center.x + r * 0.55f, center.y - r * 0.35f), r * 0.85f)) }
    val result = Path().apply { op(moon, cut, androidx.compose.ui.graphics.PathOperation.Difference) }
    drawPath(result, color)
}

/** Analog clock face for a time zone with a day/night tinted dial. */
@Composable
fun AnalogClock(now: Instant, zone: ZoneId, isDay: Boolean, modifier: Modifier = Modifier) {
    val sun = LocalSunColors.current
    val scheme = MaterialTheme.colorScheme
    val t = now.atZone(zone)
    val dial = if (isDay) scheme.surfaceContainerHighest else sun.night
    val hands = if (isDay) scheme.onSurface else Color(0xFFEEF1FA)
    Canvas(modifier.semantics { contentDescription = "Analog clock showing %d:%02d".format(t.hour, t.minute) }) {
        val r = size.minDimension / 2
        val c = center
        drawCircle(dial, r, c)
        drawCircle(if (isDay) sun.sunrise.copy(alpha = 0.6f) else sun.blue.copy(alpha = 0.5f), r, c, style = Stroke(2.dp.toPx()))
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - 90)
            val r1 = r * (if (i % 3 == 0) 0.78f else 0.84f)
            drawLine(hands.copy(alpha = 0.6f), Offset(c.x + cos(a).toFloat() * r1, c.y + sin(a).toFloat() * r1),
                Offset(c.x + cos(a).toFloat() * r * 0.9f, c.y + sin(a).toFloat() * r * 0.9f), strokeWidth = (if (i % 3 == 0) 2.dp else 1.dp).toPx())
        }
        val minuteAngle = t.minute * 6f + t.second * 0.1f
        val hourAngle = (t.hour % 12) * 30f + t.minute * 0.5f
        rotate(hourAngle, c) { drawLine(hands, c, Offset(c.x, c.y - r * 0.5f), strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round) }
        rotate(minuteAngle, c) { drawLine(hands, c, Offset(c.x, c.y - r * 0.75f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round) }
        rotate(t.second * 6f, c) { drawLine(sun.sunset, c, Offset(c.x, c.y - r * 0.8f), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round) }
        drawCircle(sun.sunset, 3.dp.toPx(), c)
    }
}

/** Compass-style view of the Sun's azimuth and a small elevation gauge. */
@Composable
fun SunPositionDial(azimuth: Double, elevation: Double, modifier: Modifier = Modifier) {
    val sun = LocalSunColors.current
    val scheme = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Canvas(Modifier.size(110.dp).semantics { contentDescription = "Sun azimuth %.0f degrees".format(azimuth) }) {
            val r = size.minDimension / 2 - 6.dp.toPx()
            drawCircle(scheme.outlineVariant, r, center, style = Stroke(1.5.dp.toPx()))
            listOf(0, 90, 180, 270).forEach { d ->
                val a = Math.toRadians(d - 90.0)
                drawLine(scheme.onSurfaceVariant, Offset(center.x + cos(a).toFloat() * (r - 8.dp.toPx()), center.y + sin(a).toFloat() * (r - 8.dp.toPx())),
                    Offset(center.x + cos(a).toFloat() * r, center.y + sin(a).toFloat() * r), strokeWidth = 2.dp.toPx())
            }
            val a = Math.toRadians(azimuth - 90.0)
            val p = Offset(center.x + cos(a).toFloat() * r * 0.78f, center.y + sin(a).toFloat() * r * 0.78f)
            drawLine((if (elevation > 0) sun.sunrise else sun.blue).copy(alpha = 0.6f), center, p, strokeWidth = 2.dp.toPx())
            drawCircle(if (elevation > 0) sun.sunrise else sun.blue, 7.dp.toPx(), p)
            drawCircle(scheme.onSurface, 2.dp.toPx(), center)
        }
        Canvas(Modifier.size(width = 110.dp, height = 70.dp).semantics { contentDescription = "Sun elevation %.0f degrees".format(elevation) }) {
            val baseY = size.height - 4.dp.toPx()
            val r = min(size.width / 2, size.height) - 6.dp.toPx()
            val c = Offset(size.width / 2, baseY)
            drawArc(scheme.outlineVariant, 180f, 180f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(1.5.dp.toPx()))
            drawLine(scheme.onSurfaceVariant, Offset(c.x - r, baseY), Offset(c.x + r, baseY), strokeWidth = 1.dp.toPx())
            val e = elevation.coerceIn(-20.0, 90.0)
            val ang = Math.toRadians(-e)
            val p = Offset(c.x + cos(ang).toFloat() * r * 0.85f, c.y + sin(ang).toFloat() * r * 0.85f)
            drawLine(if (e > 0) sun.sunrise else sun.blue, c, p, strokeWidth = 2.dp.toPx())
            drawCircle(if (e > 0) sun.sunrise else sun.blue, 6.dp.toPx(), p)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(Spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(Spacing.sm)); action() }
    }
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = valueColor)
    }
}
