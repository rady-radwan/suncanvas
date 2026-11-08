package com.suncanvas.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import com.suncanvas.app.AppContainer
import com.suncanvas.app.data.db.LocationEntity
import com.suncanvas.app.data.zone
import com.suncanvas.app.widget.model.WidgetSizeClass
import com.suncanvas.app.widget.model.WidgetSpec
import com.suncanvas.app.widget.model.WidgetTemplates
import com.suncanvas.app.widget.render.WidgetCity
import com.suncanvas.app.widget.render.WidgetData
import java.time.Instant

data class WidgetModel(
    val appWidgetId: Int,
    val spec: WidgetSpec,
    val data: WidgetData,
    val primaryLocationId: Long?,
)

class WidgetDataLoader(private val context: Context, private val container: AppContainer) {

    fun sizeClassFor(appWidgetId: Int): WidgetSizeClass {
        val info = runCatching { AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId) }.getOrNull()
        return when (info?.provider?.className) {
            SunWidgetSmallReceiver::class.java.name -> WidgetSizeClass.SMALL
            SunWidgetSquareReceiver::class.java.name -> WidgetSizeClass.SQUARE
            SunWidgetLargeReceiver::class.java.name -> WidgetSizeClass.LARGE
            else -> WidgetSizeClass.WIDE
        }
    }

    suspend fun load(appWidgetId: Int, now: Instant = Instant.now()): WidgetModel {
        val instance = container.widgets.getOrCreateInstance(appWidgetId, WidgetTemplates.defaultFor(sizeClassFor(appWidgetId)))
        val spec = WidgetSpec.fromJson(instance.specJson) ?: WidgetTemplates.defaultFor(sizeClassFor(appWidgetId)).spec
        return WidgetModel(appWidgetId, spec, dataFor(spec, now), spec.locationId)
    }

    /** Builds render data for any spec (used by widgets and by the Studio preview). */
    suspend fun dataFor(spec: WidgetSpec, now: Instant = Instant.now()): WidgetData {
        val settings = container.settings.current()
        val primary = container.locations.resolve(spec.locationId)
        val all = container.locations.getAll()
        val others: List<LocationEntity> = if (spec.extraLocationIds.isNotEmpty()) {
            spec.extraLocationIds.mapNotNull { id -> all.firstOrNull { it.id == id } }
        } else {
            all.filter { it.id != primary?.id && it.inCityList }.sortedWith(compareByDescending<LocationEntity> { it.isFavorite }.thenBy { it.sortOrder }).take(5)
        }
        return WidgetData(
            primary = primary?.let { city(it, now) },
            others = others.filter { it.id != primary?.id }.map { city(it, now) },
            now = now,
            use24h = settings.use24h(context),
            dateStyle = settings.dateStyle,
        )
    }

    private fun city(l: LocationEntity, now: Instant) = WidgetCity(
        title = l.displayName,
        country = l.countryName,
        zone = l.zone,
        snapshot = container.solar.snapshot(l, now),
    )
}
