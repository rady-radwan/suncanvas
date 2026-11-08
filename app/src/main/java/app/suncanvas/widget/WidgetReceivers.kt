package app.suncanvas.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import app.suncanvas.container
import app.suncanvas.widget.model.WidgetSizeClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/*
 * Four receivers give the launcher's widget picker four sensible starting sizes (2×1, 2×2, 4×2, 4×4).
 * All of them are freely resizable and share the same GlanceAppWidget and rendering engine.
 */
abstract class BaseSunWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SunCanvasWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetScheduler.ensureScheduled(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetScheduler.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (WidgetUpdater.allAppWidgetIds(context).isEmpty()) WidgetScheduler.cancel(context)
    }
}

class SunWidgetSmallReceiver : BaseSunWidgetReceiver()
class SunWidgetSquareReceiver : BaseSunWidgetReceiver()
class SunWidgetWideReceiver : BaseSunWidgetReceiver()
class SunWidgetLargeReceiver : BaseSunWidgetReceiver()

object WidgetUpdater {
    val receivers = listOf(
        SunWidgetSmallReceiver::class.java,
        SunWidgetSquareReceiver::class.java,
        SunWidgetWideReceiver::class.java,
        SunWidgetLargeReceiver::class.java,
    )

    fun receiverFor(sizeClass: WidgetSizeClass): Class<out BaseSunWidgetReceiver> = when (sizeClass) {
        WidgetSizeClass.SMALL -> SunWidgetSmallReceiver::class.java
        WidgetSizeClass.SQUARE -> SunWidgetSquareReceiver::class.java
        WidgetSizeClass.WIDE -> SunWidgetWideReceiver::class.java
        WidgetSizeClass.LARGE -> SunWidgetLargeReceiver::class.java
    }

    fun allAppWidgetIds(context: Context): List<Int> {
        val mgr = AppWidgetManager.getInstance(context)
        return receivers.flatMap { cls -> mgr.getAppWidgetIds(ComponentName(context, cls)).toList() }
    }

    /** Re-renders every placed widget with fresh data. */
    suspend fun updateAll(context: Context) {
        val ids = allAppWidgetIds(context)
        context.container.widgets.pruneInstances(ids.toSet())
        ids.forEach { update(context, it) }
    }

    suspend fun update(context: Context, appWidgetId: Int) {
        val manager = GlanceAppWidgetManager(context)
        val glanceId = runCatching { manager.getGlanceIdBy(appWidgetId) }.getOrNull() ?: return
        updateAppWidgetState(context, glanceId) { it[SunCanvasWidget.TICK_KEY] = System.currentTimeMillis() }
        SunCanvasWidget().update(context, glanceId)
    }

    /** Fire-and-forget refresh from non-suspending callers (settings changes, receivers). */
    fun requestUpdateAll(context: Context) {
        val app = context.applicationContext
        app.container.scope.launch {
            runCatching { updateAll(app) }
            WidgetScheduler.scheduleNextBoundary(app)
        }
    }
}

/** Receives the result of a pin request started from Widget Studio and binds the chosen design. */
class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val specJson = intent.getStringExtra(EXTRA_SPEC_JSON)
        val designId = intent.getLongExtra(EXTRA_DESIGN_ID, -1L).takeIf { it > 0 }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || specJson == null) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val spec = app.suncanvas.widget.model.WidgetSpec.fromJson(specJson)
                if (spec != null) context.container.widgets.saveInstance(appWidgetId, spec, designId)
                WidgetUpdater.update(context, appWidgetId)
                WidgetScheduler.ensureScheduled(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SPEC_JSON = "spec_json"
        const val EXTRA_DESIGN_ID = "design_id"
    }
}
