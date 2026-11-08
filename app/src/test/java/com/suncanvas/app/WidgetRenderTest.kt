package com.suncanvas.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.suncanvas.app.core.DateStyle
import com.suncanvas.app.data.SolarRepository
import com.suncanvas.app.data.db.LocationEntity
import com.suncanvas.app.data.db.LocationSource
import com.suncanvas.app.data.db.SolarCacheDao
import com.suncanvas.app.data.db.SolarCacheEntity
import com.suncanvas.app.data.zone
import com.suncanvas.app.widget.model.BackgroundType
import com.suncanvas.app.widget.model.WidgetShape
import com.suncanvas.app.widget.model.WidgetTemplates
import com.suncanvas.app.widget.render.LiveKind
import com.suncanvas.app.widget.render.WidgetCity
import com.suncanvas.app.widget.render.WidgetData
import com.suncanvas.app.widget.render.WidgetRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

/**
 * Renders every template at every common size and three times of day with real (native) graphics.
 * Fails on any crash; also writes PNGs to app/build/widget-renders for visual review in CI artifacts.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "xxhdpi")
class WidgetRenderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val noCache = object : SolarCacheDao {
        override suspend fun get(key: String): SolarCacheEntity? = null
        override suspend fun put(entity: SolarCacheEntity) = Unit
        override suspend fun prune(olderThan: Long) = Unit
    }
    private val solar = SolarRepository(noCache)

    private fun loc(id: Long, name: String, lat: Double, lon: Double, tz: String, country: String) =
        LocationEntity(id = id, name = name, latitude = lat, longitude = lon, timeZoneId = tz, source = LocationSource.CITY, countryName = country)

    private val dallas = loc(1, "Dallas", 32.7767, -96.7970, "America/Chicago", "United States")
    private val others = listOf(
        loc(2, "Cairo", 30.0444, 31.2357, "Africa/Cairo", "Egypt"),
        loc(3, "London", 51.5074, -0.1278, "Europe/London", "United Kingdom"),
        loc(4, "Tokyo", 35.6762, 139.6503, "Asia/Tokyo", "Japan"),
        loc(5, "Tromsø", 69.6492, 18.9553, "Europe/Oslo", "Norway"),
    )

    private fun data(now: Instant, use24h: Boolean = false) = WidgetData(
        primary = WidgetCity(dallas.displayName, dallas.countryName, dallas.zone, solar.snapshot(dallas, now)),
        others = others.map { WidgetCity(it.displayName, it.countryName, it.zone, solar.snapshot(it, now)) },
        now = now,
        use24h = use24h,
        dateStyle = DateStyle.MEDIUM,
    )

    private val times = mapOf(
        "noon" to Instant.parse("2026-11-08T18:05:00Z"),
        "golden" to Instant.parse("2026-11-08T23:10:00Z"),
        "night" to Instant.parse("2026-11-09T04:00:00Z"),
    )
    private val sizes = listOf(1 to 1, 2 to 1, 2 to 2, 4 to 1, 4 to 2, 4 to 4)

    private fun dp(cellsW: Int, cellsH: Int) = (cellsW * 74f + (cellsW - 1) * 8f) to (cellsH * 92f + (cellsH - 1) * 8f - 16f).coerceAtLeast(48f)

    @Test
    fun allTemplatesRenderAtAllSizesWithoutCrashing() {
        val renderer = WidgetRenderer(context)
        val out = File("build/widget-renders").apply { mkdirs() }
        var count = 0
        for (t in WidgetTemplates.all) {
            for ((label, now) in times) {
                val d = data(now, use24h = label == "night")
                for ((cw, ch) in sizes) {
                    val (w, h) = dp(cw, ch)
                    val r = renderer.render(t.spec, d, w, h, 2.625f, live = false)
                    assertTrue(r.bitmap.width > 0 && r.bitmap.height > 0)
                    assertTrue("non-live render must not reserve slots", r.slots.isEmpty())
                    if (label == "noon" || (cw to ch) == (t.sizeClass.cellsW to t.sizeClass.cellsH)) {
                        File(out, "${t.id}_${cw}x${ch}_$label.png").outputStream().use { r.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                    count++
                }
            }
        }
        assertEquals(WidgetTemplates.all.size * times.size * sizes.size, count)
    }

    @Test
    fun liveRenderReservesClockSlotsInsideTheBitmap() {
        val renderer = WidgetRenderer(context)
        val spec = WidgetTemplates.byId("digital")!!.spec
        val r = renderer.render(spec, data(times.getValue("noon")), 304f, 176f, 2.625f, live = true)
        val clock = r.slots.filter { it.kind == LiveKind.CLOCK }
        assertEquals(1, clock.size)
        val s = clock.first()
        assertTrue(s.rect.left >= -20 && s.rect.right <= r.bitmap.width + 20 && s.rect.top >= -20 && s.rect.bottom <= r.bitmap.height + 20)
        assertEquals("America/Chicago", s.zoneId)
        val world = renderer.render(WidgetTemplates.byId("world_clock")!!.spec, data(times.getValue("noon")), 304f, 260f, 2.625f, live = true)
        assertTrue("one live clock per visible city", world.slots.count { it.kind == LiveKind.CLOCK } >= 3)
        assertTrue(world.slots.filter { it.kind == LiveKind.CLOCK }.map { it.zoneId }.toSet().size >= 3)
    }

    @Test
    fun transparentBackgroundLeavesCornersTransparent() {
        val renderer = WidgetRenderer(context)
        val base = WidgetTemplates.byId("minimal")!!.spec
        val transparent = base.copy(background = base.background.copy(type = BackgroundType.TRANSPARENT, opacity = 0f))
        val r = renderer.render(transparent, data(times.getValue("noon")), 156f, 76f, 2.625f, live = false)
        assertEquals(0, android.graphics.Color.alpha(r.bitmap.getPixel(1, 1)))
        val circle = base.copy(shape = WidgetShape.CIRCLE, background = base.background.copy(type = BackgroundType.SOLID, opacity = 1f))
        val rc = renderer.render(circle, data(times.getValue("noon")), 156f, 156f, 2.625f, live = false)
        assertEquals("circle corners are transparent", 0, android.graphics.Color.alpha(rc.bitmap.getPixel(2, 2)))
        assertEquals("circle centre is opaque", 255, android.graphics.Color.alpha(rc.bitmap.getPixel(rc.bitmap.width / 2, rc.bitmap.height / 2)))
    }
}
