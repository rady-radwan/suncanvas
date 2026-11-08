package com.suncanvas.app.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import com.suncanvas.app.MainActivity
import com.suncanvas.app.R
import com.suncanvas.app.container
import com.suncanvas.app.widget.model.Align
import com.suncanvas.app.widget.render.FontResources
import com.suncanvas.app.widget.render.LiveKind
import com.suncanvas.app.widget.render.LiveSlot
import com.suncanvas.app.widget.render.RenderOutput
import java.time.Instant

/**
 * The single Glance widget used by every SunCanvas receiver. Each placed widget reads its own
 * stored [com.suncanvas.app.widget.model.WidgetSpec] (by appWidgetId), so widgets are fully independent.
 *
 * Rendering: a bitmap face from [com.suncanvas.app.widget.render.WidgetRenderer] (shapes, gradients,
 * images, custom fonts, arcs) + native TextClock / Chronometer views for anything that ticks.
 */
class SunCanvasWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val loader = context.container.widgetLoader
        val initial = loader.load(appWidgetId)
        provideContent {
            val tick = currentState(TICK_KEY) ?: 0L
            val model by produceState(initialValue = initial, tick) {
                value = loader.load(appWidgetId)
            }
            WidgetContent(model)
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        context.container.widgets.deleteInstance(appWidgetId)
    }

    @Composable
    private fun WidgetContent(model: WidgetModel) {
        val context = LocalContext.current
        val size = LocalSize.current
        val density = context.resources.displayMetrics.density
        val output: RenderOutput = remember(model, size) {
            context.container.renderer.render(model.spec, model.data, size.width.value, size.height.value, density, live = true)
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_LOCATION_ID, model.primaryLocationId ?: -1L)
            putExtra(MainActivity.EXTRA_APP_WIDGET_ID, model.appWidgetId)
        }
        Box(modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(intent))) {
            Image(
                provider = ImageProvider(output.bitmap),
                contentDescription = output.contentDescription,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            // Glance containers hold at most 10 children, so live views are grouped.
            output.slots.chunked(8).take(3).forEach { group ->
                Box(modifier = GlanceModifier.fillMaxSize()) {
                    group.forEach { slot -> LiveSlotView(context, slot, output.scale) }
                }
            }
        }
    }

    @Composable
    private fun LiveSlotView(context: Context, slot: LiveSlot, scale: Float) {
        val left = slot.rect.left / scale
        val top = slot.rect.top / scale
        val w = slot.rect.width() / scale
        val h = slot.rect.height() / scale
        val horizontal = when (slot.align) {
            Align.START -> Alignment.Horizontal.Start
            Align.CENTER -> Alignment.Horizontal.CenterHorizontally
            Align.END -> Alignment.Horizontal.End
        }
        Box(modifier = GlanceModifier.fillMaxSize().padding(start = left.dp, top = top.dp), contentAlignment = Alignment.TopStart) {
            Box(modifier = GlanceModifier.size(w.dp, h.dp), contentAlignment = Alignment(horizontal, Alignment.Vertical.CenterVertically)) {
                AndroidRemoteViews(remoteViews = liveRemoteViews(context, slot, scale))
            }
        }
    }

    companion object {
        val TICK_KEY = longPreferencesKey("tick")

        fun liveRemoteViews(context: Context, slot: LiveSlot, scale: Float): RemoteViews {
            val rv = RemoteViews(context.packageName, FontResources.liveLayout(slot.font, slot.weight))
            val sizeDp = slot.textSizePx / scale
            when (slot.kind) {
                LiveKind.CLOCK, LiveKind.AMPM -> {
                    rv.setViewVisibility(R.id.live_chrono, View.GONE)
                    rv.setViewVisibility(R.id.live_clock, View.VISIBLE)
                    rv.setTextColor(R.id.live_clock, slot.color)
                    rv.setTextViewTextSize(R.id.live_clock, TypedValue.COMPLEX_UNIT_DIP, sizeDp)
                    rv.setCharSequence(R.id.live_clock, "setFormat12Hour", slot.pattern)
                    rv.setCharSequence(R.id.live_clock, "setFormat24Hour", slot.pattern)
                    rv.setString(R.id.live_clock, "setTimeZone", slot.zoneId)
                }
                LiveKind.COUNTDOWN -> {
                    rv.setViewVisibility(R.id.live_clock, View.GONE)
                    rv.setViewVisibility(R.id.live_chrono, View.VISIBLE)
                    rv.setTextColor(R.id.live_chrono, slot.color)
                    rv.setTextViewTextSize(R.id.live_chrono, TypedValue.COMPLEX_UNIT_DIP, sizeDp)
                    val remaining = (slot.countdownTarget?.toEpochMilli() ?: 0L) - Instant.now().toEpochMilli()
                    rv.setChronometer(R.id.live_chrono, SystemClock.elapsedRealtime() + remaining, null, true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) rv.setChronometerCountDown(R.id.live_chrono, true)
                }
            }
            return rv
        }
    }
}
