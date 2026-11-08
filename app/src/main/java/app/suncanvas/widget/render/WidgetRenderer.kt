package app.suncanvas.widget.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import app.suncanvas.core.Durations
import app.suncanvas.core.TimeFormatter
import app.suncanvas.widget.model.Align
import app.suncanvas.widget.model.FontWeightChoice
import app.suncanvas.widget.model.TextCase
import app.suncanvas.widget.model.WidgetElement
import app.suncanvas.widget.model.WidgetFont
import app.suncanvas.widget.model.WidgetLayout
import app.suncanvas.widget.model.WidgetShape
import app.suncanvas.widget.model.WidgetSpec
import app.suncanvas.widget.render.WidgetPainter.Icon
import app.suncanvas.solar.DayType
import app.suncanvas.solar.NoaaSolarPosition
import app.suncanvas.solar.SolarAltitude
import app.suncanvas.solar.SolarEventType
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws a complete widget face into a bitmap. The exact same code renders the in-app live preview
 * and the home-screen widget, so what the user designs is what they get.
 *
 * When [live] is true, the current time (and optionally countdowns) are *not* painted; instead
 * [LiveSlot]s describe where the host should place native TextClock / Chronometer views, which keep
 * time perfectly without waking the app every minute.
 */
class WidgetRenderer(context: Context) {

    val painter = WidgetPainter(context.applicationContext)

    fun render(spec: WidgetSpec, data: WidgetData, widthDp: Float, heightDp: Float, density: Float, live: Boolean): RenderOutput {
        // Keep each bitmap under ~3 MB: RemoteViews has a total bitmap memory budget.
        val maxPixels = 750_000f
        val areaAtDensity = widthDp * heightDp * density * density
        val scale = if (areaAtDensity > maxPixels) sqrt(maxPixels / (widthDp * heightDp)) else density
        val w = max(1, (widthDp * scale).roundToInt())
        val h = max(1, (heightDp * scale).roundToInt())
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val frame = Frame(canvas, spec, data, scale, w.toFloat(), h.toFloat(), live)
        frame.draw()
        return RenderOutput(bitmap, frame.slots, scale, frame.description())
    }

    private inner class Frame(
        val c: Canvas,
        val spec: WidgetSpec,
        val data: WidgetData,
        val s: Float,
        val w: Float,
        val h: Float,
        val live: Boolean,
    ) {
        val slots = mutableListOf<LiveSlot>()
        val fmt = TimeFormatter(data.use24h, data.dateStyle)
        val city = data.primary
        val snap = city?.snapshot
        val zone = city?.zone
        val now: Instant = data.now
        val t = spec.typography
        val col = spec.colors
        val geo = painter.geometry(spec, w, h, s)
        val u = geo.content
        val wDp = u.width() / s
        val hDp = u.height() / s
        val elevation = snap?.position?.elevation ?: -30.0

        fun dp(v: Float) = v * s
        fun has(e: WidgetElement) = spec.has(e)
        fun row(weight: Float, f: (RectF) -> Unit): Pair<Float, (RectF) -> Unit> = weight to f

        fun draw() {
            painter.drawBackground(c, spec, geo, s, elevation)
            if (spec.shape == WidgetShape.CORNER_ACCENT) painter.drawCornerAccent(c, geo, col.accent, spec.decorOpacity)
            painter.drawBorder(c, spec, geo, s)
            if (snap == null || zone == null) {
                text("Open SunCanvas to choose a location", u, dp(13f), Role.LABEL, col.secondaryText, Align.CENTER)
                return
            }
            when (spec.layout) {
                WidgetLayout.STACK -> stack(u)
                WidgetLayout.CLOCK -> clockLayout(u)
                WidgetLayout.SOLAR_DIAL -> solarDial()
                WidgetLayout.PROGRESS_RING -> progressRing()
                WidgetLayout.ARC -> arcLayout(u)
                WidgetLayout.BAR -> barLayout(u)
                WidgetLayout.DASHBOARD -> dashboard(u)
                WidgetLayout.MULTI_CITY -> multiCity(u)
                WidgetLayout.GOLDEN -> golden(u)
                WidgetLayout.ICON -> iconLayout(u)
                WidgetLayout.SPLIT -> split(u)
                WidgetLayout.NEXT_EVENT -> nextEvent(u)
            }
            if (has(WidgetElement.DAY_NIGHT) && spec.layout !in setOf(WidgetLayout.MULTI_CITY, WidgetLayout.SOLAR_DIAL, WidgetLayout.PROGRESS_RING)) {
                val r = dp(7f)
                val cx = geo.bounds.right - max(dp(12f), geo.radius * 0.45f)
                val cy = geo.bounds.top + max(dp(12f), geo.radius * 0.45f)
                painter.drawIcon(c, if (snap.isDay) Icon.SUN else Icon.MOON, cx, cy, r * 2, col.icon.alpha(spec.textOpacity))
            }
        }

        fun description(): String {
            val sn = snap ?: return "SunCanvas widget"
            val z = zone ?: return "SunCanvas widget"
            return "${city!!.title}: sunrise ${fmt.time(sn.today.sunrise, z)}, sunset ${fmt.time(sn.today.sunset, z)}"
        }

        // ------------------------------------------------------------ text

        fun paintFor(role: Role, size: Float, color: Int): Paint {
            val (font, weight) = when (role) {
                Role.CLOCK -> (t.clockFont ?: t.font) to t.clockWeight
                Role.TITLE -> t.font to (if (t.weight == FontWeightChoice.LIGHT) FontWeightChoice.REGULAR else FontWeightChoice.BOLD)
                Role.LABEL -> t.font to (if (t.weight == FontWeightChoice.BOLD) FontWeightChoice.REGULAR else t.weight)
                Role.VALUE -> t.font to t.weight
            }
            return Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                typeface = painter.typeface(font, weight)
                textSize = size
                this.color = color.alpha(spec.textOpacity)
                letterSpacing = t.letterSpacing + (if (role == Role.LABEL) 0.06f else 0f)
                if (t.shadow) setShadowLayer(dp(3f), 0f, dp(1f), 0x99000000.toInt())
            }
        }

        fun cased(text: String, role: Role): String {
            val cs = if (role == Role.LABEL) t.labelCase else t.case
            return when (cs) {
                TextCase.AS_IS -> text
                TextCase.UPPER -> text.uppercase(Locale.getDefault())
                TextCase.LOWER -> text.lowercase(Locale.getDefault())
                TextCase.TITLE -> text.split(' ').joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) } }
            }
        }

        fun roleScale(role: Role) = when (role) {
            Role.CLOCK -> t.scale * t.clockScale
            Role.LABEL -> t.scale * t.labelScale
            else -> t.scale
        }

        /** Fits [raw] into [r] (shrinking only), draws it, returns the drawn text width. */
        fun text(raw: String, r: RectF, size: Float, role: Role, color: Int, align: Align, measureOnly: Boolean = false): Float {
            if (raw.isEmpty() || r.width() <= 1 || r.height() <= 1) return 0f
            val str = cased(raw, role)
            val p = paintFor(role, size * roleScale(role), color)
            fitPaint(p, str, r)
            val tw = p.measureText(str)
            if (measureOnly) return tw
            val x = when (align) {
                Align.START -> r.left
                Align.CENTER -> r.centerX() - tw / 2
                Align.END -> r.right - tw
            }
            val fm = p.fontMetrics
            val baseline = r.centerY() - (fm.ascent + fm.descent) / 2
            c.drawText(str, x, baseline, p)
            return tw
        }

        fun fitPaint(p: Paint, str: String, r: RectF) {
            val fm = p.fontMetrics
            val lineH = fm.descent - fm.ascent
            if (lineH > r.height()) p.textSize *= r.height() / lineH
            val tw = p.measureText(str)
            if (tw > r.width()) p.textSize *= r.width() / tw
        }

        /** Places the current time: a native live clock slot, or painted pixels for previews. */
        fun clock(r: RectF, size: Float, align: Align, color: Int = col.text, z: java.time.ZoneId = zone!!, ampmBelow: Boolean = false) {
            val (main, ampm) = fmt.clockParts(now, z)
            val sample = main.replace(Regex("[0-9]"), "8")
            val p = paintFor(Role.CLOCK, size * roleScale(Role.CLOCK), color)
            val ampmSize = p.textSize * 0.32f
            val ampmPaint = paintFor(Role.LABEL, ampmSize, color)
            val ampmW = if (ampm != null && !ampmBelow) ampmPaint.measureText(ampm) + dp(4f) else 0f
            val avail = RectF(r.left, r.top, r.right - ampmW, r.bottom)
            fitPaint(p, sample, avail)
            ampmPaint.textSize = p.textSize * 0.32f
            val tw = p.measureText(sample)
            val total = tw + if (ampm != null && !ampmBelow) ampmPaint.measureText(ampm) + dp(4f) else 0f
            val x = when (align) {
                Align.START -> r.left
                Align.CENTER -> r.centerX() - total / 2
                Align.END -> r.right - total
            }
            val fm = p.fontMetrics
            val baseline = r.centerY() - (fm.ascent + fm.descent) / 2
            val clockRect = RectF(x, baseline + fm.ascent, x + tw, baseline + fm.descent)
            if (live && spec.liveClock) {
                slots += LiveSlot(
                    kind = LiveKind.CLOCK, rect = RectF(clockRect).apply { inset(-dp(6f), 0f) }, textSizePx = p.textSize,
                    color = color.alpha(spec.textOpacity), font = t.clockFont ?: t.font, weight = t.clockWeight,
                    align = align, zoneId = z.id, pattern = fmt.textClockPattern, shadow = t.shadow,
                )
            } else {
                val px = when (align) {
                    Align.START -> x
                    Align.CENTER -> x + (tw - p.measureText(main)) / 2
                    Align.END -> x + tw - p.measureText(main)
                }
                c.drawText(main, px, baseline, p)
            }
            if (ampm != null) {
                val ax: Float
                val ay: Float
                if (ampmBelow) {
                    ax = clockRect.centerX() - ampmPaint.measureText(ampm) / 2
                    ay = baseline + fm.descent + ampmPaint.textSize
                } else {
                    ax = x + tw + dp(4f)
                    ay = baseline
                }
                if (live && spec.liveClock) {
                    val afm = ampmPaint.fontMetrics
                    slots += LiveSlot(
                        kind = LiveKind.AMPM, rect = RectF(ax - dp(2f), ay + afm.ascent, ax + ampmPaint.measureText(ampm) + dp(8f), ay + afm.descent),
                        textSizePx = ampmPaint.textSize, color = color.alpha(spec.textOpacity), font = t.font,
                        weight = FontWeightChoice.REGULAR, align = Align.START, zoneId = z.id, pattern = "a", shadow = t.shadow,
                    )
                } else {
                    c.drawText(ampm, ax, ay, ampmPaint)
                }
            }
        }

        /** Countdown text ("2h 05m") or a native ticking chronometer slot. */
        fun countdown(target: Instant?, r: RectF, size: Float, role: Role, color: Int, align: Align) {
            if (target == null) { text("—", r, size, role, color, align); return }
            if (live && spec.liveCountdown) {
                val p = paintFor(role, size * roleScale(role), color)
                fitPaint(p, "88:88:88", r)
                slots += LiveSlot(
                    kind = LiveKind.COUNTDOWN, rect = RectF(r), textSizePx = p.textSize, color = color.alpha(spec.textOpacity),
                    font = if (role == Role.CLOCK) (t.clockFont ?: t.font) else t.font,
                    weight = if (role == Role.CLOCK) t.clockWeight else t.weight, align = align,
                    zoneId = zone!!.id, countdownTarget = target, shadow = t.shadow,
                )
            } else {
                val d = Duration.between(now, target)
                val str = if (role == Role.CLOCK) Durations.clock(d) else Durations.countdown(now, target).removePrefix("in ")
                text(str, r, size, role, color, align)
            }
        }

        fun icon(icon: Icon, cx: Float, cy: Float, size: Float, color: Int = col.icon) {
            if (!has(WidgetElement.ICONS)) return
            painter.drawIcon(c, icon, cx, cy, size, color.alpha(spec.textOpacity))
        }

        fun timeStr(i: Instant?) = when {
            i != null -> fmt.time(i, zone!!)
            snap!!.today.dayType == DayType.POLAR_DAY -> "No set"
            snap.today.dayType == DayType.POLAR_NIGHT -> "No rise"
            else -> "—"
        }

        val sunrise get() = snap!!.today.sunrise
        val sunset get() = snap!!.today.sunset

        // ------------------------------------------------------------ layouts

        /** One labelled info line: [icon] LABEL ........ value */
        fun infoRow(r: RectF, ic: Icon?, label: String, value: String, valueColor: Int, align: Align) {
            val iconSize = min(r.height() * 0.78f, dp(26f))
            var left = r.left
            var right = r.right
            val showIcon = ic != null && has(WidgetElement.ICONS)
            when (align) {
                Align.START -> {
                    if (showIcon) { icon(ic!!, left + iconSize / 2, r.centerY(), iconSize, valueColor); left += iconSize + dp(8f) }
                    val vw = text(value, RectF(left, r.top, right, r.bottom), r.height() * 0.62f, Role.VALUE, col.text, Align.END)
                    text(label, RectF(left, r.top, right - vw - dp(8f), r.bottom), r.height() * 0.42f, Role.LABEL, col.secondaryText, Align.START)
                }
                Align.END -> {
                    if (showIcon) { icon(ic!!, right - iconSize / 2, r.centerY(), iconSize, valueColor); right -= iconSize + dp(8f) }
                    val vw = text(value, RectF(left, r.top, right, r.bottom), r.height() * 0.62f, Role.VALUE, col.text, Align.START)
                    text(label, RectF(left + vw + dp(8f), r.top, right, r.bottom), r.height() * 0.42f, Role.LABEL, col.secondaryText, Align.END)
                }
                Align.CENTER -> {
                    val valueSize = r.height() * 0.62f
                    val p = paintFor(Role.VALUE, valueSize * roleScale(Role.VALUE), col.text)
                    val vStr = cased(value, Role.VALUE)
                    fitPaint(p, vStr, RectF(r.left, r.top, r.right - (if (showIcon) iconSize + dp(6f) else 0f), r.bottom))
                    val vw = p.measureText(vStr)
                    val total = vw + if (showIcon) iconSize + dp(6f) else 0f
                    var x = r.centerX() - total / 2
                    if (showIcon) { icon(ic!!, x + iconSize / 2, r.centerY(), iconSize, valueColor); x += iconSize + dp(6f) }
                    text(value, RectF(x, r.top, x + vw + 1f, r.bottom), valueSize, Role.VALUE, col.text, Align.START)
                }
            }
        }

        fun stack(r: RectF) {
            data class Row(val weight: Float, val draw: (RectF) -> Unit)
            val rows = mutableListOf<Row>()
            val align = spec.align
            val horizontalSun = hDp < 64 && wDp > hDp * 1.8f
            if (has(WidgetElement.CITY)) rows += Row(1.1f) { text(city!!.title, it, it.height() * 0.8f, Role.TITLE, col.text, align) }
            val sub = listOfNotNull(
                city!!.country.takeIf { has(WidgetElement.COUNTRY) && it.isNotBlank() },
                fmt.date(now, zone!!).takeIf { has(WidgetElement.DATE) },
                TimeFormatter.zoneAbbreviation(zone, now).takeIf { has(WidgetElement.TZ_ABBR) },
            ).joinToString(" · ")
            if (sub.isNotEmpty()) rows += Row(0.8f) { text(sub, it, it.height() * 0.75f, Role.LABEL, col.secondaryText, align) }
            if (has(WidgetElement.TIME)) rows += Row(2.2f) { clock(it, it.height(), align) }

            val sunItems = mutableListOf<Triple<Icon, String, Pair<String, Int>>>()
            if (has(WidgetElement.SUNRISE)) sunItems += Triple(Icon.SUNRISE, "Sunrise", timeStr(sunrise) to col.sunrise)
            if (has(WidgetElement.SUNSET)) sunItems += Triple(Icon.SUNSET, "Sunset", timeStr(sunset) to col.sunset)
            if (has(WidgetElement.DAYLIGHT)) sunItems += Triple(Icon.HOURGLASS, "Daylight", Durations.hm(snap!!.today.daylight) to col.accent)
            if (has(WidgetElement.SOLAR_NOON)) sunItems += Triple(Icon.NOON, "Solar noon", fmt.time(snap!!.today.solarNoon, zone) to col.icon)
            if (has(WidgetElement.GOLDEN_HOUR)) snap!!.nextGoldenHour?.let { gw ->
                sunItems += Triple(Icon.GOLDEN, "Golden hour", "${fmt.time(gw.start, zone)}–${fmt.time(gw.end, zone)}" to col.sunrise)
            }
            val next = snap!!.nextSunEvent
            if (has(WidgetElement.NEXT_EVENT) && next != null) {
                val isRise = next.type == SolarEventType.SUNRISE
                sunItems += Triple(if (isRise) Icon.SUNRISE else Icon.SUNSET, if (isRise) "Next sunrise" else "Next sunset",
                    fmt.time(next.instant, zone) to if (isRise) col.sunrise else col.sunset)
            }
            if (has(WidgetElement.COUNTDOWN_SUNRISE)) snap.next.nextSunrise?.let {
                sunItems += Triple(Icon.SUNRISE, "Sunrise in", Durations.countdown(now, it).removePrefix("in ") to col.sunrise)
            }
            if (has(WidgetElement.COUNTDOWN_SUNSET)) snap.next.nextSunset?.let {
                sunItems += Triple(Icon.SUNSET, "Sunset in", Durations.countdown(now, it).removePrefix("in ") to col.sunset)
            }

            if (horizontalSun && sunItems.isNotEmpty()) {
                rows += Row(1.6f) { rr ->
                    val n = sunItems.size
                    val cw = rr.width() / n
                    sunItems.forEachIndexed { i, (ic, _, v) ->
                        val cell = RectF(rr.left + cw * i, rr.top, rr.left + cw * (i + 1), rr.bottom)
                        infoRow(cell.apply { inset(dp(2f), 0f) }, ic, "", v.first, v.second, Align.CENTER)
                    }
                }
            } else {
                sunItems.forEach { (ic, label, v) -> rows += Row(1f) { rr -> infoRow(rr, ic, label, v.first, v.second, align) } }
            }
            if (has(WidgetElement.PROGRESS)) rows += Row(0.45f) { rr -> progressBar(rr) }
            layoutRows(r, rows.map { it.weight to it.draw })
        }

        fun layoutRows(r: RectF, rows: List<Pair<Float, (RectF) -> Unit>>) {
            if (rows.isEmpty()) return
            val gap = dp(4f) * spec.spacing
            val total = rows.sumOf { it.first.toDouble() }.toFloat()
            val avail = r.height() - gap * (rows.size - 1)
            // Cap row height so short content stays compact and vertically centred.
            val unit = min(avail / total, dp(30f) * spec.spacing.coerceAtLeast(0.6f))
            val used = unit * total + gap * (rows.size - 1)
            var y = r.top + (r.height() - used) / 2
            rows.forEach { (wt, fn) ->
                val rh = unit * wt
                fn(RectF(r.left, y, r.right, y + rh))
                y += rh + gap
            }
        }

        fun progressBar(r: RectF, fraction: Float? = snap!!.daylightProgress) {
            val th = min(r.height() * 0.5f, dp(6f))
            val track = RectF(r.left, r.centerY() - th / 2, r.right, r.centerY() + th / 2)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col.text.alpha(0.18f * spec.decorOpacity) }
            c.drawRoundRect(track, th / 2, th / 2, p)
            if (fraction != null) {
                val fill = RectF(track.left, track.top, track.left + track.width() * fraction, track.bottom)
                p.shader = painter.linear(track, 0f, intArrayOf(col.sunrise, col.sunset))
                c.drawRoundRect(fill, th / 2, th / 2, p)
                p.shader = null
                painter.glow(c, fill.right, track.centerY(), th * 2.5f, col.arc, spec.decorOpacity)
                p.color = col.arc
                c.drawCircle(fill.right, track.centerY(), th * 0.9f, p)
            }
        }

        fun clockLayout(r: RectF) {
            val wide = wDp / hDp > 2.3f
            val sunPairs = listOfNotNull(
                if (has(WidgetElement.SUNRISE)) Triple(Icon.SUNRISE, timeStr(sunrise), col.sunrise) else null,
                if (has(WidgetElement.SUNSET)) Triple(Icon.SUNSET, timeStr(sunset), col.sunset) else null,
            )
            val sub = listOfNotNull(
                city!!.title.takeIf { has(WidgetElement.CITY) },
                fmt.date(now, zone!!).takeIf { has(WidgetElement.DATE) },
                TimeFormatter.zoneAbbreviation(zone, now).takeIf { has(WidgetElement.TZ_ABBR) },
            ).joinToString(" · ")
            if (wide && sunPairs.isNotEmpty()) {
                val leftW = r.width() * 0.62f
                val left = RectF(r.left, r.top, r.left + leftW, r.bottom)
                val right = RectF(left.right + dp(8f), r.top, r.right, r.bottom)
                val clockH = if (sub.isNotEmpty()) left.height() * 0.68f else left.height()
                if (has(WidgetElement.TIME)) clock(RectF(left.left, left.top, left.right, left.top + clockH), clockH * 0.9f, Align.START)
                if (sub.isNotEmpty()) text(sub, RectF(left.left, left.top + clockH, left.right, left.bottom), dp(14f), Role.LABEL, col.secondaryText, Align.START)
                val rowH = min(right.height() / sunPairs.size, dp(34f))
                var y = right.centerY() - rowH * sunPairs.size / 2
                sunPairs.forEach { (ic, v, cc) -> infoRow(RectF(right.left, y, right.right, y + rowH), ic, "", v, cc, Align.END); y += rowH }
                return
            }
            val rows = mutableListOf<Pair<Float, (RectF) -> Unit>>()
            if (has(WidgetElement.TIME)) rows += row(2.6f) { rr -> clock(rr, rr.height(), spec.align) }
            if (sub.isNotEmpty()) rows += row(0.75f) { rr -> text(sub, rr, rr.height() * 0.8f, Role.LABEL, col.secondaryText, spec.align) }
            if (sunPairs.isNotEmpty()) rows += row(1.1f) { rr ->
                val cw = rr.width() / sunPairs.size
                sunPairs.forEachIndexed { i, (ic, v, cc) ->
                    infoRow(RectF(rr.left + cw * i + dp(2f), rr.top, rr.left + cw * (i + 1) - dp(2f), rr.bottom), ic, "", v, cc, Align.CENTER)
                }
            }
            if (has(WidgetElement.PROGRESS)) rows += row(0.4f) { rr -> progressBar(rr) }
            layoutRowsLoose(r, rows)
        }

        /** Like [layoutRows] but lets rows grow to fill the widget (for big clocks). */
        fun layoutRowsLoose(r: RectF, rows: List<Pair<Float, (RectF) -> Unit>>) {
            if (rows.isEmpty()) return
            val gap = dp(3f) * spec.spacing
            val total = rows.sumOf { it.first.toDouble() }.toFloat()
            val unit = (r.height() - gap * (rows.size - 1)) / total
            var y = r.top
            rows.forEach { (wt, fn) -> fn(RectF(r.left, y, r.right, y + unit * wt)); y += unit * wt + gap }
        }

        fun dayCurve(points: Int = 96): DoubleArray {
            val start = snap!!.localDate.atStartOfDay(zone!!).toInstant().toEpochMilli()
            val len = snap.today.dayLength.toMillis()
            return DoubleArray(points) { i ->
                NoaaSolarPosition.geometricElevation(start + len * i / points, city!!.snapshot.location.latitude, city.snapshot.location.longitude)
            }
        }

        fun skyColor(e: Double): Int = when {
            e >= SolarAltitude.GOLDEN_UPPER -> col.arc
            e >= SolarAltitude.HORIZON -> col.sunrise
            e >= SolarAltitude.CIVIL -> col.sunset.alpha(0.75f)
            e >= SolarAltitude.ASTRONOMICAL -> col.accent.alpha(0.45f)
            else -> col.text.alpha(0.12f)
        }

        /** 24-hour dial: midnight at the bottom, noon at the top. */
        fun solarDial() {
            val b = geo.bounds
            val d = min(b.width(), b.height())
            val cx = b.centerX()
            val cy = b.centerY()
            val ringW = d * 0.075f
            val rr = d / 2 - ringW / 2 - dp(6f)
            val oval = RectF(cx - rr, cy - rr, cx + rr, cy + rr)
            val curve = dayCurve()
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ringW; strokeCap = Paint.Cap.BUTT }
            val seg = 360f / curve.size
            curve.forEachIndexed { i, e ->
                p.color = skyColor(e).alpha(spec.decorOpacity.coerceAtLeast(0.25f))
                // i = 0 is local midnight → bottom (90°); time runs clockwise.
                c.drawArc(oval, 90f + i * seg, seg + 0.6f, false, p)
            }
            // Hour ticks.
            val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col.text.alpha(0.45f * spec.decorOpacity); strokeWidth = dp(1.2f) }
            for (hh in 0 until 24 step 3) {
                val a = Math.toRadians(90.0 + hh * 15.0)
                val r1 = rr - ringW * 0.9f
                val r2 = rr - ringW * (if (hh % 6 == 0) 1.6f else 1.25f)
                c.drawLine(cx + cos(a).toFloat() * r1, cy + sin(a).toFloat() * r1, cx + cos(a).toFloat() * r2, cy + sin(a).toFloat() * r2, tick)
            }
            // Sun marker at the current local time.
            val dayStart = snap!!.localDate.atStartOfDay(zone!!).toInstant()
            val frac = (Duration.between(dayStart, now).toMillis().toDouble() / snap.today.dayLength.toMillis()).coerceIn(0.0, 1.0)
            val a = Math.toRadians(90.0 + frac * 360.0)
            val sx = cx + cos(a).toFloat() * rr
            val sy = cy + sin(a).toFloat() * rr
            painter.glow(c, sx, sy, ringW * 2.4f, if (snap.isDay) col.arc else col.accent, spec.decorOpacity)
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (snap.isDay) col.icon else col.text }
            c.drawCircle(sx, sy, ringW * 0.75f, dot)
            dot.color = Color.WHITE.alpha(0.9f)
            c.drawCircle(sx, sy, ringW * 0.32f, dot)

            val inner = rr - ringW * 1.8f
            val box = RectF(cx - inner * 0.82f, cy - inner * 0.62f, cx + inner * 0.82f, cy + inner * 0.62f)
            val rows = mutableListOf<Pair<Float, (RectF) -> Unit>>()
            if (has(WidgetElement.DAY_NIGHT)) rows += row(0.7f) { r -> icon(if (snap.isDay) Icon.SUN else Icon.MOON, r.centerX(), r.centerY(), r.height(), if (snap.isDay) col.icon else col.accent) }
            if (has(WidgetElement.TIME)) rows += row(1.8f) { r -> clock(r, r.height(), Align.CENTER) }
            if (has(WidgetElement.CITY)) rows += row(0.6f) { r -> text(city!!.title, r, r.height() * 0.85f, Role.LABEL, col.secondaryText, Align.CENTER) }
            val sun = listOfNotNull(
                if (has(WidgetElement.SUNRISE)) "↑ " + timeStr(sunrise) else null,
                if (has(WidgetElement.SUNSET)) "↓ " + timeStr(sunset) else null,
            ).joinToString("   ")
            if (sun.isNotEmpty()) rows += row(0.65f) { r -> text(sun, r, r.height() * 0.85f, Role.VALUE, col.secondaryText, Align.CENTER) }
            layoutRowsLoose(box, rows)
        }

        fun progressRing() {
            val b = geo.bounds
            val d = min(b.width(), b.height())
            val cx = b.centerX()
            val cy = b.centerY()
            val ringW = d * (if (spec.shape == WidgetShape.RING) 0.06f else 0.085f)
            val rr = d / 2 - ringW / 2 - d * (if (spec.shape == WidgetShape.RING) 0.045f else 0.07f)
            val oval = RectF(cx - rr, cy - rr, cx + rr, cy + rr)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ringW; strokeCap = Paint.Cap.ROUND }
            p.color = col.text.alpha(0.14f * spec.decorOpacity.coerceAtLeast(0.3f))
            c.drawArc(oval, 0f, 360f, false, p)
            val frac = snap!!.daylightProgress
            val isDay = frac != null
            val nightFrac = if (!isDay) nightProgress() else null
            val f = frac ?: nightFrac ?: 0f
            p.shader = android.graphics.SweepGradient(cx, cy,
                if (isDay) intArrayOf(col.sunrise, col.arc, col.sunset, col.sunrise) else intArrayOf(col.accent, col.accent.alpha(0.6f), col.accent),
                null)
            val m = android.graphics.Matrix().apply { setRotate(-90f, cx, cy) }
            p.shader.setLocalMatrix(m)
            if (f > 0.002f) c.drawArc(oval, -90f, 360f * f, false, p)
            p.shader = null
            val a = Math.toRadians(-90.0 + 360.0 * f)
            val ex = cx + cos(a).toFloat() * rr
            val ey = cy + sin(a).toFloat() * rr
            painter.glow(c, ex, ey, ringW * 2f, if (isDay) col.arc else col.accent, spec.decorOpacity)
            c.drawCircle(ex, ey, ringW * 0.62f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

            val inner = rr - ringW
            val box = RectF(cx - inner * 0.78f, cy - inner * 0.62f, cx + inner * 0.78f, cy + inner * 0.62f)
            val rows = mutableListOf<Pair<Float, (RectF) -> Unit>>()
            if (has(WidgetElement.DAY_NIGHT)) rows += row(0.6f) { r -> icon(if (isDay) Icon.SUN else Icon.MOON, r.centerX(), r.centerY(), r.height(), if (isDay) col.icon else col.accent) }
            if (isDay) {
                rows += row(1.6f) { r -> text("${(f * 100).roundToInt()}%", r, r.height(), Role.CLOCK, col.text, Align.CENTER) }
                rows += row(0.55f) { r -> text("of daylight", r, r.height() * 0.9f, Role.LABEL, col.secondaryText, Align.CENTER) }
                if (has(WidgetElement.SUNSET)) rows += row(0.6f) { r -> text("Sunset ${timeStr(snap.next.nextSunset)}", r, r.height() * 0.9f, Role.VALUE, col.sunset, Align.CENTER) }
            } else {
                rows += row(0.55f) { r -> text("Sunrise in", r, r.height() * 0.9f, Role.LABEL, col.secondaryText, Align.CENTER) }
                rows += row(1.5f) { r -> countdown(snap.next.nextSunrise, r, r.height(), Role.CLOCK, col.text, Align.CENTER) }
                if (has(WidgetElement.SUNRISE) || has(WidgetElement.SUNSET)) rows += row(0.6f) { r ->
                    text("at ${timeStr(snap.next.nextSunrise)}", r, r.height() * 0.9f, Role.VALUE, col.sunrise, Align.CENTER)
                }
            }
            layoutRowsLoose(box, rows)
        }

        fun nightProgress(): Float? {
            val prevSet = listOfNotNull(snap!!.yesterday.sunset, snap.today.sunset).filter { it.isBefore(now) }.maxOrNull() ?: return null
            val nextRise = snap.next.nextSunrise ?: return null
            val total = Duration.between(prevSet, nextRise).seconds.toFloat()
            if (total <= 0f) return null
            return (Duration.between(prevSet, now).seconds / total).coerceIn(0f, 1f)
        }

        fun arcLayout(r: RectF) {
            val topRowH = if (has(WidgetElement.CITY) || has(WidgetElement.PROGRESS) || has(WidgetElement.TIME)) min(r.height() * 0.2f, dp(22f)) else 0f
            val bottomRowH = if (has(WidgetElement.SUNRISE) || has(WidgetElement.SUNSET)) min(r.height() * 0.24f, dp(26f)) else 0f
            if (topRowH > 0) {
                val tr = RectF(r.left, r.top, r.right, r.top + topRowH)
                if (has(WidgetElement.CITY)) text(city!!.title, RectF(tr.left, tr.top, tr.centerX() + tr.width() * 0.2f, tr.bottom), tr.height() * 0.8f, Role.TITLE, col.text, Align.START)
                if (has(WidgetElement.TIME)) clock(RectF(tr.centerX(), tr.top, tr.right, tr.bottom), tr.height(), Align.END)
                else if (has(WidgetElement.PROGRESS)) {
                    val pr = snap!!.daylightProgress
                    val str = if (pr != null) "${(pr * 100).roundToInt()}% of daylight" else "Night"
                    text(str, RectF(tr.centerX(), tr.top, tr.right, tr.bottom), tr.height() * 0.7f, Role.LABEL, col.secondaryText, Align.END)
                }
            }
            val area = RectF(r.left, r.top + topRowH + dp(4f), r.right, r.bottom - bottomRowH)
            drawSunArc(area)
            if (bottomRowH > 0) {
                val br = RectF(r.left, r.bottom - bottomRowH, r.right, r.bottom)
                val half = br.width() / 2
                if (has(WidgetElement.SUNRISE)) infoRow(RectF(br.left, br.top, br.left + half, br.bottom), Icon.SUNRISE, "", timeStr(sunrise), col.sunrise, Align.START)
                if (has(WidgetElement.SUNSET)) infoRow(RectF(br.left + half, br.top, br.right, br.bottom), Icon.SUNSET, "", timeStr(sunset), col.sunset, Align.END)
            }
        }

        /** Semi-elliptical sun path with the horizon, the travelled portion and the Sun (or Moon at night). */
        fun drawSunArc(area: RectF) {
            if (area.height() < dp(10f)) return
            val horizonY = area.bottom - area.height() * 0.12f
            val rx = area.width() / 2 * 0.92f
            val ry = min(area.height() * 0.82f, rx)
            val cx = area.centerX()
            val oval = RectF(cx - rx, horizonY - ry, cx + rx, horizonY + ry)
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col.text.alpha(0.35f * spec.decorOpacity); strokeWidth = dp(1.2f) }
            c.drawLine(area.left, horizonY, area.right, horizonY, line)
            val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = dp(2f)
                color = col.arc.alpha(0.45f * spec.decorOpacity.coerceAtLeast(0.3f))
                pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
            }
            c.drawArc(oval, 180f, 180f, false, track)
            val f = snap!!.daylightProgress
            if (f != null) {
                val done = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = dp(3f); strokeCap = Paint.Cap.ROUND
                    shader = painter.linear(oval, 0f, intArrayOf(col.sunrise, col.arc, col.sunset))
                }
                c.drawArc(oval, 180f, 180f * f, false, done)
                val ang = Math.toRadians(180.0 + 180.0 * f)
                val sx = cx + cos(ang).toFloat() * rx
                val sy = horizonY + sin(ang).toFloat() * ry
                painter.glow(c, sx, sy, dp(22f), col.arc, spec.decorOpacity)
                c.drawCircle(sx, sy, dp(7f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col.icon })
                c.drawCircle(sx, sy, dp(3.5f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE.alpha(0.9f) })
            } else {
                // Night: the Sun is below the horizon — show the Moon and a dimmed path.
                val np = nightProgress() ?: 0.5f
                val ang = Math.toRadians(180.0 - 180.0 * np)
                val sx = cx + cos(ang).toFloat() * rx
                val sy = horizonY + sin(ang).toFloat() * ry * 0.18f
                c.drawCircle(sx, sy, dp(4f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col.sunset.alpha(0.5f) })
                painter.drawIcon(c, Icon.MOON, cx, horizonY - ry * 0.55f, dp(20f), col.text.alpha(0.85f * spec.textOpacity))
            }
        }

        fun barLayout(r: RectF) {
            val showLabels = hDp > 60
            val rowH = if (showLabels) r.height() * 0.62f else r.height()
            val br = RectF(r.left, r.bottom - rowH, r.right, r.bottom)
            if (showLabels) {
                val lr = RectF(r.left, r.top, r.right, r.bottom - rowH)
                val next = snap!!.nextSunEvent
                val label = when {
                    has(WidgetElement.CITY) -> city!!.title
                    next != null -> (if (next.type == SolarEventType.SUNRISE) "Sunrise " else "Sunset ") + Durations.countdown(now, next.instant)
                    else -> ""
                }
                text(label, lr, lr.height() * 0.8f, Role.LABEL, col.secondaryText, Align.CENTER)
            }
            val sideW = min(br.width() * 0.32f, dp(110f))
            val iconSize = min(br.height() * 0.8f, dp(22f))
            val leftR = RectF(br.left, br.top, br.left + sideW, br.bottom)
            val rightR = RectF(br.right - sideW, br.top, br.right, br.bottom)
            infoRow(leftR, Icon.SUNRISE, "", timeStr(sunrise), col.sunrise, Align.START)
            infoRow(rightR, Icon.SUNSET, "", timeStr(sunset), col.sunset, Align.END)
            val mid = RectF(leftR.right + dp(8f), br.top, rightR.left - dp(8f), br.bottom)
            if (has(WidgetElement.PROGRESS) && mid.width() > dp(20f)) progressBar(mid)
            else if (mid.width() > dp(30f) && has(WidgetElement.DAYLIGHT)) text(Durations.hm(snap!!.today.daylight), mid, iconSize, Role.LABEL, col.secondaryText, Align.CENTER)
        }

        fun dashboard(r: RectF) {
            val large = hDp > 200
            var top = r.top
            val headerH = min(dp(if (large) 40f else 24f), r.height() * 0.22f)
            if (has(WidgetElement.CITY) || has(WidgetElement.TIME) || has(WidgetElement.DATE)) {
                val hr = RectF(r.left, top, r.right, top + headerH)
                val titleW = if (has(WidgetElement.TIME)) hr.width() * 0.58f else hr.width()
                if (has(WidgetElement.CITY)) {
                    if (has(WidgetElement.DATE) && large) {
                        text(city!!.title, RectF(hr.left, hr.top, hr.left + titleW, hr.top + hr.height() * 0.6f), hr.height() * 0.55f, Role.TITLE, col.text, Align.START)
                        text(fmt.date(now, zone!!), RectF(hr.left, hr.top + hr.height() * 0.6f, hr.left + titleW, hr.bottom), hr.height() * 0.35f, Role.LABEL, col.secondaryText, Align.START)
                    } else {
                        text(city!!.title, RectF(hr.left, hr.top, hr.left + titleW, hr.bottom), hr.height() * 0.75f, Role.TITLE, col.text, Align.START)
                    }
                }
                if (has(WidgetElement.TIME)) clock(RectF(hr.left + titleW, hr.top, hr.right, hr.bottom), hr.height(), Align.END)
                top += headerH + dp(6f)
            }
            if (large && has(WidgetElement.SUN_ARC)) {
                val arcH = (r.bottom - top) * 0.36f
                drawSunArc(RectF(r.left, top, r.right, top + arcH))
                top += arcH + dp(6f)
            }
            data class Tile(val icon: Icon, val label: String, val value: String, val color: Int)
            val tiles = mutableListOf<Tile>()
            if (has(WidgetElement.SUNRISE)) tiles += Tile(Icon.SUNRISE, "Sunrise", timeStr(sunrise), col.sunrise)
            if (has(WidgetElement.SUNSET)) tiles += Tile(Icon.SUNSET, "Sunset", timeStr(sunset), col.sunset)
            if (has(WidgetElement.DAYLIGHT)) tiles += Tile(Icon.HOURGLASS, "Daylight", Durations.hm(snap!!.today.daylight), col.accent)
            val next = snap!!.nextSunEvent
            if (has(WidgetElement.NEXT_EVENT) && next != null) {
                val rise = next.type == SolarEventType.SUNRISE
                tiles += Tile(if (rise) Icon.SUNRISE else Icon.SUNSET, if (rise) "Sunrise in" else "Sunset in", Durations.countdown(now, next.instant).removePrefix("in "), if (rise) col.sunrise else col.sunset)
            }
            if (has(WidgetElement.SOLAR_NOON)) tiles += Tile(Icon.NOON, "Solar noon", fmt.time(snap.today.solarNoon, zone!!), col.icon)
            if (has(WidgetElement.GOLDEN_HOUR)) snap.nextGoldenHour?.let { tiles += Tile(Icon.GOLDEN, "Golden hour", fmt.time(it.start, zone!!), col.sunrise) }
            if (tiles.isEmpty()) return
            val area = RectF(r.left, top, r.right, r.bottom)
            val cols = when {
                wDp / max(1f, area.height() / s) > 3.2f -> min(tiles.size, 4)
                tiles.size <= 2 -> tiles.size
                else -> 2
            }
            val rowsN = (tiles.size + cols - 1) / cols
            val gap = dp(6f)
            val tw = (area.width() - gap * (cols - 1)) / cols
            val th = min((area.height() - gap * (rowsN - 1)) / rowsN, dp(64f))
            val usedH = th * rowsN + gap * (rowsN - 1)
            val y0 = area.top + (area.height() - usedH) / 2
            val tileBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col.text.alpha(0.08f * spec.decorOpacity) }
            tiles.forEachIndexed { i, tile ->
                val cx = i % cols
                val cy = i / cols
                val tr = RectF(area.left + cx * (tw + gap), y0 + cy * (th + gap), area.left + cx * (tw + gap) + tw, y0 + cy * (th + gap) + th)
                c.drawRoundRect(tr, dp(12f), dp(12f), tileBg)
                val inner = RectF(tr).apply { inset(dp(8f), dp(5f)) }
                val iconSize = min(inner.height() * 0.5f, dp(20f))
                if (has(WidgetElement.ICONS)) icon(tile.icon, inner.left + iconSize / 2, inner.top + iconSize / 2, iconSize, tile.color)
                val labelLeft = if (has(WidgetElement.ICONS)) inner.left + iconSize + dp(5f) else inner.left
                text(tile.label, RectF(labelLeft, inner.top, inner.right, inner.top + iconSize), iconSize * 0.62f, Role.LABEL, col.secondaryText, Align.START)
                text(tile.value, RectF(inner.left, inner.top + iconSize + dp(1f), inner.right, inner.bottom), (inner.height() - iconSize) * 0.85f, Role.VALUE, col.text, Align.START)
            }
        }

        fun multiCity(r: RectF) {
            val cities = listOfNotNull(city) + data.others
            val minRow = dp(30f)
            val n = max(1, min(cities.size, (r.height() / minRow).toInt()))
            val rowH = min(r.height() / n, dp(52f))
            var y = r.top + (r.height() - rowH * n) / 2
            val divider = Paint().apply { color = col.text.alpha(0.10f * spec.decorOpacity); strokeWidth = dp(1f) }
            cities.take(n).forEachIndexed { i, wc ->
                val row = RectF(r.left, y, r.right, y + rowH)
                if (i > 0) c.drawLine(row.left, row.top, row.right, row.top, divider)
                val sn = wc.snapshot
                var left = row.left
                if (has(WidgetElement.DAY_NIGHT)) {
                    val sz = min(rowH * 0.42f, dp(16f))
                    painter.drawIcon(c, if (sn.isDay) Icon.SUN else Icon.MOON, left + sz / 2, row.centerY(), sz, (if (sn.isDay) col.icon else col.accent).alpha(spec.textOpacity))
                    left += sz + dp(8f)
                }
                val showSun = has(WidgetElement.SUNRISE) || has(WidgetElement.SUNSET)
                val showTime = has(WidgetElement.TIME)
                val rightW = (if (showTime) row.width() * 0.30f else 0f) + (if (showSun) row.width() * (if (showTime) 0.30f else 0.42f) else 0f)
                val nameR = RectF(left, row.top, row.right - rightW - dp(6f), row.bottom)
                val sub = listOfNotNull(
                    wc.country.takeIf { has(WidgetElement.COUNTRY) && it.isNotBlank() },
                    TimeFormatter.zoneAbbreviation(wc.zone, now).takeIf { has(WidgetElement.TZ_ABBR) },
                    fmt.date(now, wc.zone).takeIf { has(WidgetElement.DATE) },
                ).joinToString(" · ")
                if (sub.isNotEmpty() && rowH > dp(36f)) {
                    text(wc.title, RectF(nameR.left, nameR.top + rowH * 0.12f, nameR.right, nameR.centerY() + rowH * 0.06f), rowH * 0.34f, Role.TITLE, col.text, Align.START)
                    text(sub, RectF(nameR.left, nameR.centerY() + rowH * 0.06f, nameR.right, nameR.bottom - rowH * 0.12f), rowH * 0.24f, Role.LABEL, col.secondaryText, Align.START)
                } else {
                    text(wc.title, nameR, rowH * 0.36f, Role.TITLE, col.text, Align.START)
                }
                var right = row.right
                if (showTime) {
                    val tr = RectF(right - row.width() * 0.30f, row.top + rowH * 0.12f, right, row.bottom - rowH * 0.12f)
                    clock(tr, tr.height(), Align.END, col.text, wc.zone)
                    right = tr.left - dp(4f)
                }
                if (showSun) {
                    val sr = RectF(right - row.width() * (if (showTime) 0.30f else 0.42f), row.top, right, row.bottom)
                    val parts = listOfNotNull(
                        if (has(WidgetElement.SUNRISE)) "↑" + fmt.time(sn.today.sunrise, wc.zone) else null,
                        if (has(WidgetElement.SUNSET)) "↓" + fmt.time(sn.today.sunset, wc.zone) else null,
                    )
                    if (rowH > dp(38f) && parts.size == 2) {
                        text(parts[0], RectF(sr.left, sr.top + rowH * 0.1f, sr.right, sr.centerY()), rowH * 0.28f, Role.VALUE, col.sunrise, Align.END)
                        text(parts[1], RectF(sr.left, sr.centerY(), sr.right, sr.bottom - rowH * 0.1f), rowH * 0.28f, Role.VALUE, col.sunset, Align.END)
                    } else {
                        text(parts.joinToString("  "), sr, rowH * 0.3f, Role.VALUE, col.secondaryText, Align.END)
                    }
                }
                y += rowH
            }
        }

        fun golden(r: RectF) {
            val today = snap!!.today
            val windows = listOfNotNull(
                today.goldenHourMorning?.let { "Morning" to it },
                today.goldenHourEvening?.let { "Evening" to it },
            )
            val rows = mutableListOf<Pair<Float, (RectF) -> Unit>>()
            val title = if (snap.inGoldenHour) "Golden hour now" else "Golden hour"
            rows += row(1.1f) { rr ->
                val iconS = rr.height() * 0.9f
                val tw = text(title, RectF(rr.left + (if (has(WidgetElement.ICONS)) iconS + dp(6f) else 0f), rr.top, rr.right, rr.bottom), rr.height() * 0.8f, Role.TITLE, col.text, Align.START)
                if (tw > 0) icon(Icon.GOLDEN, rr.left + iconS / 2, rr.centerY(), iconS, col.icon)
            }
            if (has(WidgetElement.CITY)) rows += row(0.65f) { rr -> text(city!!.title + " · " + fmt.date(now, zone!!), rr, rr.height() * 0.8f, Role.LABEL, col.secondaryText, Align.START) }
            if (windows.isEmpty()) {
                rows += row(1f) { rr -> text(if (today.dayType == DayType.POLAR_NIGHT) "No golden hour today" else "Sun stays high today", rr, rr.height() * 0.7f, Role.VALUE, col.text, Align.START) }
            }
            windows.forEach { (label, wdw) ->
                rows += row(1f) { rr ->
                    val active = now in wdw
                    if (active) {
                        val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col.text.alpha(0.12f * spec.decorOpacity) }
                        c.drawRoundRect(RectF(rr).apply { inset(-dp(4f), 0f) }, dp(8f), dp(8f), hp)
                    }
                    val range = "${fmt.time(wdw.start, zone!!)} – ${fmt.time(wdw.end, zone)}"
                    infoRow(rr, null, label, range, col.sunrise, Align.START)
                }
            }
            if (has(WidgetElement.SUNSET)) rows += row(0.75f) { rr -> text("Sunset ${timeStr(sunset)}", rr, rr.height() * 0.8f, Role.LABEL, col.secondaryText, Align.START) }
            layoutRows(r, rows)
        }

        fun iconLayout(r: RectF) {
            val next = snap!!.nextSunEvent
            val isRise = next?.type == SolarEventType.SUNRISE
            val ic = if (next == null) (if (snap.isDay) Icon.SUN else Icon.MOON) else if (isRise) Icon.SUNRISE else Icon.SUNSET
            val tiny = min(wDp, hDp) < 70
            val iconH = r.height() * (if (tiny) 0.52f else 0.5f)
            painter.drawIcon(c, ic, r.centerX(), r.top + iconH / 2 + r.height() * 0.04f, iconH, (if (isRise) col.sunrise else col.sunset).alpha(spec.textOpacity))
            val value = next?.let { fmt.time(it.instant, zone!!) } ?: if (snap.isDay) "All day" else "No sun"
            val vr = RectF(r.left, r.top + iconH + r.height() * 0.04f, r.right, r.bottom - (if (tiny) 0f else r.height() * 0.18f))
            text(value, vr, vr.height() * 0.9f, Role.VALUE, col.text, Align.CENTER)
            if (!tiny && next != null) {
                text(Durations.countdown(now, next.instant), RectF(r.left, r.bottom - r.height() * 0.18f, r.right, r.bottom), r.height() * 0.14f, Role.LABEL, col.secondaryText, Align.CENTER)
            }
        }

        fun split(r: RectF) {
            val showDaylight = has(WidgetElement.DAYLIGHT) && hDp > 70
            val bottomH = if (showDaylight) r.height() * 0.24f else 0f
            val main = RectF(r.left, r.top, r.right, r.bottom - bottomH)
            val half = main.width() / 2
            val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col.text.alpha(0.25f * spec.decorOpacity); strokeWidth = dp(1f) }
            c.drawLine(main.centerX(), main.top + main.height() * 0.15f, main.centerX(), main.bottom - main.height() * 0.15f, divider)
            fun side(rect: RectF, ic: Icon, label: String, value: String, color: Int) {
                val iconS = min(rect.height() * 0.32f, dp(30f))
                icon(ic, rect.centerX(), rect.top + iconS / 2 + rect.height() * 0.06f, iconS, color)
                val y = rect.top + (if (has(WidgetElement.ICONS)) iconS + rect.height() * 0.1f else rect.height() * 0.1f)
                val lh = (rect.bottom - y) * 0.3f
                text(label, RectF(rect.left, y, rect.right, y + lh), lh * 0.8f, Role.LABEL, col.secondaryText, Align.CENTER)
                text(value, RectF(rect.left + dp(4f), y + lh, rect.right - dp(4f), rect.bottom), (rect.bottom - y - lh) * 0.8f, Role.VALUE, col.text, Align.CENTER)
            }
            side(RectF(main.left, main.top, main.left + half, main.bottom), Icon.SUNRISE, "Sunrise", timeStr(sunrise), col.sunrise)
            side(RectF(main.left + half, main.top, main.right, main.bottom), Icon.SUNSET, "Sunset", timeStr(sunset), col.sunset)
            if (showDaylight) {
                val br = RectF(r.left, r.bottom - bottomH, r.right, r.bottom)
                val label = "${Durations.hm(snap!!.today.daylight)} of daylight  ·  ${Durations.signedMinSec(snap.daylightChange)}"
                text(label, br, br.height() * 0.6f, Role.LABEL, col.secondaryText, Align.CENTER)
            }
        }

        fun nextEvent(r: RectF) {
            val next = snap!!.nextSunEvent
            val isRise = next?.type == SolarEventType.SUNRISE
            val color = if (isRise) col.sunrise else col.sunset
            val wide = wDp / hDp > 2.4f
            if (next == null) {
                text(if (snap.isDay) "Midnight sun" else "Polar night", r, r.height() * 0.4f, Role.TITLE, col.text, Align.CENTER)
                return
            }
            val eventName = if (isRise) "Sunrise" else "Sunset"
            if (wide) {
                val iconS = min(r.height() * 0.8f, dp(32f))
                var left = r.left
                if (has(WidgetElement.ICONS)) { icon(if (isRise) Icon.SUNRISE else Icon.SUNSET, left + iconS / 2, r.centerY(), iconS, color); left += iconS + dp(8f) }
                val mid = RectF(left, r.top, left + (r.right - left) * 0.55f, r.bottom)
                text(eventName, RectF(mid.left, mid.top, mid.right, mid.centerY()), mid.height() * 0.42f, Role.LABEL, col.secondaryText, Align.START)
                text(fmt.time(next.instant, zone!!), RectF(mid.left, mid.centerY(), mid.right, mid.bottom), mid.height() * 0.48f, Role.VALUE, col.text, Align.START)
                countdown(next.instant, RectF(mid.right + dp(6f), r.top, r.right, r.bottom), r.height() * 0.5f, Role.VALUE, col.text, Align.END)
                return
            }
            val rows = mutableListOf<Pair<Float, (RectF) -> Unit>>()
            rows += row(0.7f) { rr ->
                val iconS = rr.height()
                val off = if (has(WidgetElement.ICONS)) iconS + dp(6f) else 0f
                icon(if (isRise) Icon.SUNRISE else Icon.SUNSET, rr.left + iconS / 2, rr.centerY(), iconS, color)
                text("$eventName in", RectF(rr.left + off, rr.top, rr.right, rr.bottom), rr.height() * 0.8f, Role.LABEL, col.secondaryText, Align.START)
            }
            rows += row(2.2f) { rr -> countdown(next.instant, rr, rr.height(), Role.CLOCK, col.text, Align.START) }
            rows += row(0.7f) { rr -> text("at ${fmt.time(next.instant, zone!!)}", rr, rr.height() * 0.85f, Role.VALUE, color, Align.START) }
            if (has(WidgetElement.CITY)) rows += row(0.6f) { rr -> text(city!!.title, rr, rr.height() * 0.85f, Role.LABEL, col.secondaryText, Align.START) }
            layoutRowsLoose(r, rows)
        }
    }

    enum class Role { CLOCK, TITLE, VALUE, LABEL }

    companion object {
        /** Multiplies a colour's alpha. */
        fun Int.alpha(f: Float): Int = WidgetPainter.withAlpha(this, f)
    }
}
