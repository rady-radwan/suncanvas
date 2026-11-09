package app.suncanvas.data.cities

import app.suncanvas.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Optional expanded city search using the free, key-less Open-Meteo geocoding API
 * (GeoNames-based, returns the IANA time zone). Only the typed search text is sent —
 * never the user's GPS coordinates.
 */
class OnlineGeocoder {

    @Serializable
    private data class Response(val results: List<GeoResult> = emptyList())

    @Serializable
    private data class GeoResult(
        val id: Long = 0,
        val name: String = "",
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val country_code: String = "",
        val country: String = "",
        val admin1: String = "",
        val timezone: String = "",
        val population: Long = 0,
    )

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun search(query: String, limit: Int = 20): Result<List<City>> = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val url = URL("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=$limit&language=en&format=json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "${BuildConfig.BRAND_NAME}-Android")
            }
            try {
                if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString(Response.serializer(), body).results
                    .filter { it.timezone.isNotBlank() && CityDatabase.isValidZone(it.timezone) }
                    .map {
                        City(
                            geonameId = it.id,
                            name = it.name,
                            admin1 = it.admin1,
                            countryCode = it.country_code,
                            countryName = it.country,
                            latitude = it.latitude,
                            longitude = it.longitude,
                            population = it.population,
                            timeZoneId = it.timezone,
                            fromOnline = true,
                        )
                    }
            } finally {
                conn.disconnect()
            }
        }
    }
}
