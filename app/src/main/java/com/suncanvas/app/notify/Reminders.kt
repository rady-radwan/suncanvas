package com.suncanvas.app.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.suncanvas.app.AppContainer
import com.suncanvas.app.MainActivity
import com.suncanvas.app.R
import com.suncanvas.app.container
import com.suncanvas.app.data.db.LocationEntity
import com.suncanvas.app.data.db.ReminderEntity
import com.suncanvas.app.data.db.ReminderEvent
import com.suncanvas.app.data.zone
import com.suncanvas.app.widget.WidgetScheduler
import com.suncanvas.app.widget.WidgetUpdater
import com.suncanvas.solar.SolarDay
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.abs

object ReminderNotifications {
    const val CHANNEL_SUN = "sun_events"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SUN, "Sunrise & sunset reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Optional reminders for sunrise, sunset and golden hour"
            }
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(context: Context, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_SUN)
            .setSmallIcon(R.drawable.ic_stat_sun)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}

/**
 * Schedules only the *next* occurrence of each enabled reminder with AlarmManager, computed in the
 * reminder location's own time zone. Exact alarms are used when the user allows them; otherwise a
 * 10-minute window alarm keeps battery use minimal.
 */
class ReminderScheduler(private val context: Context, private val container: AppContainer) {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun canUseExactAlarms(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    suspend fun rescheduleAll() {
        val enabled = container.settings.current().notificationsEnabled
        val reminders = container.database.reminders().getAll()
        reminders.forEach { cancel(it.id) }
        if (!enabled) return
        reminders.filter { it.enabled }.forEach { schedule(it) }
    }

    suspend fun schedule(reminder: ReminderEntity, after: Instant = Instant.now()) {
        val location = container.locations.resolve(reminder.locationId) ?: return
        val fire = nextFireTime(reminder, location, after) ?: return
        val pi = pendingIntent(reminder.id)
        val at = fire.toEpochMilli()
        if (canUseExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, pi)
        }
    }

    fun cancel(reminderId: Long) {
        alarms.cancel(pendingIntent(reminderId))
    }

    fun nextFireTime(reminder: ReminderEntity, location: LocationEntity, after: Instant): Instant? {
        val zone = location.zone
        var date = after.atZone(zone).toLocalDate().minusDays(1)
        repeat(370) {
            val day = container.solar.day(location, date)
            val event = eventInstant(day, reminder.event)
            val fire = event?.plusSeconds(reminder.offsetMinutes * 60L)
            if (fire != null && fire.isAfter(after.plusSeconds(20))) return fire
            date = date.plusDays(1)
        }
        return null
    }

    private fun pendingIntent(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, id.toInt(),
        Intent(context, ReminderReceiver::class.java).putExtra(ReminderReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        fun eventInstant(day: SolarDay, e: ReminderEvent): Instant? = when (e) {
            ReminderEvent.SUNRISE -> day.sunrise
            ReminderEvent.SUNSET -> day.sunset
            ReminderEvent.GOLDEN_MORNING_START -> day.goldenHourMorning?.takeIf { !it.startClipped }?.start
            ReminderEvent.GOLDEN_MORNING_END -> day.goldenHourMorning?.takeIf { !it.endClipped }?.end
            ReminderEvent.GOLDEN_EVENING_START -> day.goldenHourEvening?.takeIf { !it.startClipped }?.start
            ReminderEvent.GOLDEN_EVENING_END -> day.goldenHourEvening?.takeIf { !it.endClipped }?.end
            ReminderEvent.BLUE_EVENING_START -> day.blueHourEvening?.takeIf { !it.startClipped }?.start
            ReminderEvent.CIVIL_DAWN -> day.civilDawn
            ReminderEvent.SOLAR_NOON -> day.solarNoon
        }

        fun describe(r: ReminderEntity): String {
            val m = abs(r.offsetMinutes)
            val name = r.event.label.lowercase().replaceFirstChar { it.uppercase() }
            return when {
                r.offsetMinutes == 0 -> "$name — now"
                r.offsetMinutes < 0 -> "$m min before ${r.event.label.lowercase()}"
                else -> "$m min after ${r.event.label.lowercase()}"
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) return
        val pending = goAsync()
        val c = context.container
        c.scope.launch {
            try {
                val r = c.database.reminders().get(id) ?: return@launch
                if (!r.enabled || !c.settings.current().notificationsEnabled) return@launch
                val loc = c.locations.resolve(r.locationId)
                val fmt = c.settings.current().formatter(context)
                val event = loc?.let { l ->
                    val day = c.solar.day(l, Instant.now().atZone(l.zone).toLocalDate())
                    ReminderScheduler.eventInstant(day, r.event)
                }
                val title = when {
                    r.offsetMinutes == 0 -> "${r.event.label} now"
                    r.offsetMinutes < 0 -> "${r.event.label} in ${-r.offsetMinutes} min"
                    else -> "${r.event.label} was ${r.offsetMinutes} min ago"
                }
                val text = loc?.let { "${it.displayName} · ${r.event.label} at ${fmt.time(event, it.zone)}" } ?: ""
                ReminderNotifications.post(context, id.toInt(), title, text)
                c.reminderScheduler.schedule(r, Instant.now().plusSeconds(60))
            } finally {
                pending.finish()
            }
        }
    }

    companion object { const val EXTRA_ID = "reminder_id" }
}

/** Reboot, clock or time-zone changes: reschedule reminders and refresh widgets. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val c = context.container
        c.scope.launch {
            try {
                runCatching { c.reminderScheduler.rescheduleAll() }
                if (WidgetUpdater.allAppWidgetIds(context).isNotEmpty()) {
                    runCatching { WidgetUpdater.updateAll(context) }
                    WidgetScheduler.ensureScheduled(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
