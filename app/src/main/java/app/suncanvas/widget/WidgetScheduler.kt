package app.suncanvas.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import app.suncanvas.container
import app.suncanvas.data.zone
import app.suncanvas.widget.model.WidgetSpec
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Battery-friendly widget refresh strategy:
 *  - The clock and optional countdowns are native TextClock/Chronometer views → no updates needed.
 *  - A one-off job runs exactly at the next boundary that changes what a widget shows
 *    (sunrise, sunset, golden-hour edges, local midnight of any widget's city).
 *  - A periodic job (default every 30 min, minimum 15 min allowed by Android) moves the Sun along
 *    arcs/progress rings and refreshes "remaining" values.
 * WorkManager survives reboots, so widgets are restored after the phone restarts.
 */
object WidgetScheduler {
    private const val PERIODIC = "widget-periodic"
    private const val BOUNDARY = "widget-boundary"

    fun ensureScheduled(context: Context) {
        val app = context.applicationContext
        app.container.scope.launchCatching {
            val minutes = app.container.settings.current().widgetRefreshMinutes.toLong().coerceAtLeast(15)
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(minutes, TimeUnit.MINUTES).build()
            WorkManager.getInstance(app).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
            scheduleNextBoundary(app)
        }
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(BOUNDARY)
    }

    suspend fun scheduleNextBoundary(context: Context) {
        val ids = WidgetUpdater.allAppWidgetIds(context)
        if (ids.isEmpty()) return
        val c = context.container
        val now = Instant.now()
        val allCandidates = mutableListOf<Instant>()
        val locationIds = mutableSetOf<Long?>()
        ids.forEach { id ->
            val spec = c.widgets.instance(id)?.specJson?.let { WidgetSpec.fromJson(it) }
            locationIds += spec?.locationId
            spec?.extraLocationIds?.forEach { locationIds += it }
        }
        locationIds.mapNotNull { c.locations.resolve(it) }.distinctBy { it.id }.forEach { loc ->
            val snap = c.solar.snapshot(loc, now)
            val zone = loc.zone
            val candidates = buildList {
                addAll(snap.today.events.map { it.instant })
                addAll(snap.tomorrow.events.map { it.instant })
                listOfNotNull(snap.today.goldenHourMorning, snap.today.goldenHourEvening, snap.today.blueHourMorning, snap.today.blueHourEvening)
                    .forEach { add(it.start); add(it.end) }
                add(snap.localDate.plusDays(1).atStartOfDay(zone).toInstant())
            }
            allCandidates += candidates.filter { it.isAfter(now.plusSeconds(30)) }
        }
        val target = allCandidates.minOrNull() ?: return
        val delay = Duration.between(now, target).plusSeconds(5).toMillis().coerceIn(60_000L, 6 * 3600_000L)
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(BOUNDARY, ExistingWorkPolicy.REPLACE, request)
    }
}

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            WidgetUpdater.updateAll(applicationContext)
            WidgetScheduler.scheduleNextBoundary(applicationContext)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

private fun CoroutineScope.launchCatching(block: suspend () -> Unit) = launch { runCatching { block() } }
