package com.suncanvas.app.data

import com.suncanvas.app.data.cities.City
import com.suncanvas.app.data.db.LocationDao
import com.suncanvas.app.data.db.LocationEntity
import com.suncanvas.app.data.db.LocationSource
import com.suncanvas.app.location.DeviceLocationProvider
import com.suncanvas.app.location.LocationResult
import com.suncanvas.app.location.TimeZoneResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlin.math.abs

class LocationRepository(
    private val dao: LocationDao,
    private val settings: SettingsRepository,
    private val provider: DeviceLocationProvider,
    private val resolver: TimeZoneResolver,
) {
    val all: Flow<List<LocationEntity>> = dao.observeAll()

    /** The location shown on the dashboard: explicit selection → default → first saved. */
    val active: Flow<LocationEntity?> = combine(settings.settings, dao.observeAll()) { s, list ->
        list.firstOrNull { it.id == s.activeLocationId }
            ?: list.firstOrNull { it.isDefault }
            ?: list.firstOrNull()
    }.distinctUntilChanged()

    fun observe(id: Long): Flow<LocationEntity?> = dao.observe(id)

    suspend fun get(id: Long): LocationEntity? = dao.get(id)
    suspend fun getAll(): List<LocationEntity> = dao.getAll()
    suspend fun getDefault(): LocationEntity? = dao.getDefault() ?: dao.getAll().firstOrNull()
    suspend fun getActive(): LocationEntity? = active.first()

    /** Resolves a location id that may be null ("follow default"). */
    suspend fun resolve(id: Long?): LocationEntity? = id?.let { dao.get(it) } ?: getDefault()

    suspend fun setActive(id: Long) = settings.setActiveLocation(id)

    /**
     * Requests a single foreground fix and stores it in the DEVICE row. The first location ever
     * saved becomes the default automatically.
     */
    suspend fun refreshDeviceLocation(makeActive: Boolean = true): Pair<LocationResult, LocationEntity?> {
        val result = provider.currentLocation()
        if (result !is LocationResult.Success) return result to null
        val place = resolver.resolve(result.latitude, result.longitude)
        val existing = dao.getDevice()
        val hasDefault = dao.getDefault() != null
        val entity = (existing ?: LocationEntity(
            name = place.name,
            latitude = result.latitude,
            longitude = result.longitude,
            timeZoneId = place.timeZoneId,
            source = LocationSource.DEVICE,
            inCityList = true,
            sortOrder = 0,
        )).copy(
            name = place.name,
            admin1 = place.admin1,
            countryCode = place.countryCode,
            countryName = place.countryName,
            latitude = result.latitude,
            longitude = result.longitude,
            timeZoneId = place.timeZoneId,
            geonameId = place.nearestCity?.geonameId,
            isDefault = existing?.isDefault ?: !hasDefault,
            updatedAt = System.currentTimeMillis(),
        )
        val id = if (existing == null) dao.insert(entity) else { dao.update(entity); entity.id }
        if (makeActive) settings.setActiveLocation(id)
        return result to dao.get(id)
    }

    suspend fun addCity(city: City, makeDefault: Boolean = false, makeActive: Boolean = false): Long {
        val existing = dao.getAll().firstOrNull {
            it.source != LocationSource.DEVICE &&
                ((city.geonameId != 0L && it.geonameId == city.geonameId) ||
                    (abs(it.latitude - city.latitude) < 0.01 && abs(it.longitude - city.longitude) < 0.01))
        }
        val id = existing?.id ?: dao.insert(
            LocationEntity(
                name = city.name,
                admin1 = city.admin1,
                countryCode = city.countryCode,
                countryName = city.countryName,
                latitude = city.latitude,
                longitude = city.longitude,
                timeZoneId = city.timeZoneId,
                source = if (city.fromOnline) LocationSource.ONLINE else LocationSource.CITY,
                geonameId = city.geonameId,
                sortOrder = dao.maxSortOrder() + 1,
            )
        )
        if (existing != null && !existing.inCityList) dao.update(existing.copy(inCityList = true))
        if (makeDefault || dao.getDefault() == null) dao.setDefault(id)
        if (makeActive) settings.setActiveLocation(id)
        return id
    }

    suspend fun addManual(
        name: String,
        latitude: Double,
        longitude: Double,
        timeZoneId: String,
        admin1: String = "",
        countryCode: String = "",
        countryName: String = "",
        makeDefault: Boolean = false,
        makeActive: Boolean = false,
    ): Long {
        val id = dao.insert(
            LocationEntity(
                name = name,
                admin1 = admin1,
                countryCode = countryCode,
                countryName = countryName,
                latitude = latitude,
                longitude = longitude,
                timeZoneId = timeZoneId,
                source = LocationSource.MANUAL,
                sortOrder = dao.maxSortOrder() + 1,
            )
        )
        if (makeDefault || dao.getDefault() == null) dao.setDefault(id)
        if (makeActive) settings.setActiveLocation(id)
        return id
    }

    suspend fun update(entity: LocationEntity) = dao.update(entity.copy(updatedAt = System.currentTimeMillis()))

    suspend fun rename(id: Long, label: String?) {
        dao.get(id)?.let { dao.update(it.copy(customLabel = label?.trim()?.takeIf { l -> l.isNotEmpty() })) }
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) {
        dao.get(id)?.let { dao.update(it.copy(isFavorite = favorite)) }
    }

    suspend fun setDefault(id: Long) = dao.setDefault(id)

    suspend fun delete(id: Long) {
        val wasDefault = dao.get(id)?.isDefault == true
        dao.delete(id)
        if (wasDefault) dao.getAll().firstOrNull()?.let { dao.setDefault(it.id) }
        if (settings.current().activeLocationId == id) settings.setActiveLocation(null)
    }

    suspend fun reorder(ids: List<Long>) = dao.reorder(ids)

    suspend fun resolvePlace(latitude: Double, longitude: Double) = resolver.resolve(latitude, longitude)
}
