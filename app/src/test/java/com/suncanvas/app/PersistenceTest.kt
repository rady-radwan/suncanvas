package com.suncanvas.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suncanvas.app.data.cities.CityDatabase
import com.suncanvas.app.data.db.AppDatabase
import com.suncanvas.app.data.db.LocationEntity
import com.suncanvas.app.data.db.LocationSource
import com.suncanvas.app.data.db.WidgetInstanceEntity
import com.suncanvas.app.location.TimeZoneResolver
import com.suncanvas.app.widget.model.WidgetSpec
import com.suncanvas.app.widget.model.WidgetTemplates
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Widget configurations live in Room, so they survive process death and device restarts.
 * These tests close and reopen the on-disk database to simulate a reboot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersistenceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun open(name: String) = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()

    @Test
    fun widgetConfigsAreIndependentAndSurviveRestart() = runBlocking<Unit> {
        val name = "persist-test.db"
        context.deleteDatabase(name)
        var db = open(name)
        val dallas = WidgetTemplates.byId("minimal")!!.spec.copy(locationId = 1, name = "Dallas")
        val cairo = WidgetTemplates.byId("digital")!!.spec.copy(locationId = 2, name = "Cairo")
        db.widgets().putInstance(WidgetInstanceEntity(appWidgetId = 11, specJson = dallas.toJson()))
        db.widgets().putInstance(WidgetInstanceEntity(appWidgetId = 22, specJson = cairo.toJson()))

        // Edit widget 11 only.
        val edited = dallas.copy(background = dallas.background.copy(opacity = 0f), name = "Dallas transparent")
        db.widgets().putInstance(WidgetInstanceEntity(appWidgetId = 11, specJson = edited.toJson()))
        db.close()

        // "Reboot": reopen the database from disk.
        db = open(name)
        val w11 = WidgetSpec.fromJson(db.widgets().getInstance(11)!!.specJson)!!
        val w22 = WidgetSpec.fromJson(db.widgets().getInstance(22)!!.specJson)!!
        assertEquals("Dallas transparent", w11.name)
        assertEquals(0f, w11.background.opacity)
        assertEquals(cairo, w22)
        db.close()
        context.deleteDatabase(name)
    }

    @Test
    fun locationsKeepTheirTimeZoneAndOrder() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val a = db.locations().insert(LocationEntity(name = "Tokyo", latitude = 35.68, longitude = 139.69, timeZoneId = "Asia/Tokyo", source = LocationSource.CITY, sortOrder = 1))
        val b = db.locations().insert(LocationEntity(name = "London", latitude = 51.5, longitude = -0.12, timeZoneId = "Europe/London", source = LocationSource.CITY, sortOrder = 2))
        db.locations().reorder(listOf(b, a))
        val all = db.locations().getAll()
        assertEquals(listOf("London", "Tokyo"), all.map { it.name })
        db.locations().setDefault(a)
        assertEquals("Tokyo", db.locations().getDefault()!!.name)
        assertEquals("Asia/Tokyo", db.locations().get(a)!!.timeZoneId)
        db.close()
    }

    @Test
    fun offlineTimeZoneResolution() = runBlocking<Unit> {
        val cities = CityDatabase(context)
        val resolver = TimeZoneResolver(cities)
        assertEquals("America/Chicago", resolver.resolve(32.78, -96.80).timeZoneId)
        assertEquals("Africa/Cairo", resolver.resolve(30.04, 31.24).timeZoneId)
        assertEquals("Asia/Tokyo", resolver.resolve(35.68, 139.69).timeZoneId)
        // Mid-Pacific: nautical fallback.
        assertTrue(resolver.resolve(-30.0, -140.0).timeZoneId.startsWith("Etc/GMT"))
        // Same-name cities are disambiguated by region.
        val paris = cities.search("paris")
        assertTrue(paris.any { it.countryCode == "FR" } && paris.any { it.countryCode == "US" && it.admin1 == "Texas" })
        assertEquals("FR", paris.first().countryCode)
        assertEquals("US", cities.search("paris, tx").first().countryCode)
    }
}
