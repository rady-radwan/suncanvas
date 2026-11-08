package com.suncanvas.app.widget.render

import android.graphics.Bitmap
import android.graphics.RectF
import com.suncanvas.app.core.DateStyle
import com.suncanvas.app.data.SunSnapshot
import com.suncanvas.app.widget.model.Align
import com.suncanvas.app.widget.model.FontWeightChoice
import com.suncanvas.app.widget.model.WidgetFont
import java.time.Instant
import java.time.ZoneId

data class WidgetCity(
    val title: String,
    val country: String,
    val zone: ZoneId,
    val snapshot: SunSnapshot,
)

/** Everything needed to draw a widget face. */
data class WidgetData(
    val primary: WidgetCity?,
    val others: List<WidgetCity> = emptyList(),
    val now: Instant,
    val use24h: Boolean,
    val dateStyle: DateStyle = DateStyle.MEDIUM,
)

enum class LiveKind { CLOCK, AMPM, COUNTDOWN }

/**
 * A region the host should cover with a native, self-updating view (TextClock / Chronometer)
 * instead of static pixels. Coordinates are in bitmap pixels.
 */
data class LiveSlot(
    val kind: LiveKind,
    val rect: RectF,
    val textSizePx: Float,
    val color: Int,
    val font: WidgetFont,
    val weight: FontWeightChoice,
    val align: Align,
    val zoneId: String,
    val pattern: String = "h:mm",
    val countdownTarget: Instant? = null,
    val shadow: Boolean = false,
)

data class RenderOutput(
    val bitmap: Bitmap,
    val slots: List<LiveSlot>,
    /** Pixels per dp used for this bitmap. */
    val scale: Float,
    val contentDescription: String,
)
