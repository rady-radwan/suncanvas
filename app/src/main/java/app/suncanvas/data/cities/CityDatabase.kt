package app.suncanvas.data.cities

import android.content.Context
import app.suncanvas.core.distanceKm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.time.ZoneId

data class City(
    val geonameId: Long,
    val name: String,
    val admin1: String,
    val countryCode: String,
    val countryName: String,
    val latitude: Double,
    val longitude: Double,
    val population: Long,
    val timeZoneId: String,
    val alternates: List<String> = emptyList(),
    val fromOnline: Boolean = false,
) {
    /** Disambiguating line: "Texas, United States" vs "Île-de-France, France". */
    val region: String
        get() = listOf(admin1, countryName).filter { it.isNotBlank() }.joinToString(", ")
}

/**
 * Offline world city database (~34 000 cities, GeoNames CC-BY 4.0) bundled as a TSV asset.
 * Loaded lazily on a background thread; search is by accent-insensitive prefix with
 * population ranking, so identically named cities are listed with their region and country.
 */
class CityDatabase(private val context: Context) {

    private val mutex = Mutex()
    @Volatile private var cities: List<City>? = null
    @Volatile private var keys: Array<String> = emptyArray()
    @Volatile private var countries: Map<String, String> = emptyMap()

    suspend fun ensureLoaded(): List<City> {
        cities?.let { return it }
        return mutex.withLock {
            cities ?: withContext(Dispatchers.IO) { load() }.also { cities = it }
        }
    }

    fun countryName(code: String): String = countries[code] ?: code

    private fun load(): List<City> {
        val countryMap = HashMap<String, String>(300)
        context.assets.open("countries.tsv").bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val i = line.indexOf('\t')
                if (i > 0) countryMap[line.substring(0, i)] = line.substring(i + 1)
            }
        }
        countries = countryMap
        val list = ArrayList<City>(34_500)
        context.assets.open("cities.tsv").bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.startsWith("#") || line.isBlank()) return@forEach
                val c = line.split('\t')
                if (c.size < 8) return@forEach
                list += City(
                    geonameId = c[0].toLong(),
                    name = c[1],
                    admin1 = c[2],
                    countryCode = c[3],
                    countryName = countryMap[c[3]] ?: c[3],
                    latitude = c[4].toDouble(),
                    longitude = c[5].toDouble(),
                    population = c[6].toLong(),
                    timeZoneId = c[7],
                    alternates = if (c.size > 8 && c[8].isNotBlank()) c[8].split('|') else emptyList(),
                )
            }
        }
        keys = Array(list.size) { normalize(list[it].name) }
        return list
    }

    /**
     * Search by name. Supports "paris", "paris, tx", "paris texas", "paris france".
     * Exact matches first, then prefix, then word-prefix, then alternate names; ties by population.
     */
    suspend fun search(query: String, limit: Int = 40): List<City> {
        val all = ensureLoaded()
        val raw = normalize(query).trim()
        if (raw.length < 2) return emptyList()
        val parts = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        var namePart = parts.firstOrNull() ?: return emptyList()
        var qualifier = parts.drop(1).joinToString(" ")
        return withContext(Dispatchers.Default) {
            var results = rank(all, namePart, qualifier, limit)
            if (results.isEmpty() && parts.size == 1 && namePart.contains(' ')) {
                // "paris texas" → name "paris", qualifier "texas"
                val idx = namePart.lastIndexOf(' ')
                qualifier = namePart.substring(idx + 1)
                namePart = namePart.substring(0, idx)
                results = rank(all, namePart, qualifier, limit)
            }
            results
        }
    }

    private fun rank(all: List<City>, name: String, qualifier: String, limit: Int): List<City> {
        data class Hit(val city: City, val tier: Int)
        val hits = ArrayList<Hit>()
        for (i in all.indices) {
            val key = keys[i]
            val city = all[i]
            val tier = when {
                key == name -> 0
                key.startsWith(name) -> 1
                key.contains(" $name") || key.contains("-$name") -> 2
                city.alternates.any { normalize(it).startsWith(name) } -> 3
                else -> -1
            }
            if (tier < 0) continue
            if (qualifier.isNotEmpty()) {
                val q = qualifier
                val matchesQualifier = normalize(city.admin1).startsWith(q) ||
                    normalize(city.countryName).startsWith(q) ||
                    city.countryCode.equals(q, ignoreCase = true) ||
                    usStateCode(city).equals(q, ignoreCase = true)
                if (!matchesQualifier) continue
            }
            hits += Hit(city, tier)
        }
        return hits.sortedWith(compareBy<Hit> { it.tier }.thenByDescending { it.city.population })
            .take(limit).map { it.city }
    }

    /** Nearest city to the coordinates, with distance in km. */
    suspend fun nearest(latitude: Double, longitude: Double): Pair<City, Double>? {
        val all = ensureLoaded()
        return withContext(Dispatchers.Default) {
            var best: City? = null
            var bestScore = Double.MAX_VALUE
            val cosLat = Math.cos(Math.toRadians(latitude))
            for (c in all) {
                val dLat = c.latitude - latitude
                var dLon = c.longitude - longitude
                if (dLon > 180) dLon -= 360
                if (dLon < -180) dLon += 360
                val score = dLat * dLat + (dLon * cosLat) * (dLon * cosLat)
                if (score < bestScore) { bestScore = score; best = c }
            }
            best?.let { it to distanceKm(latitude, longitude, it.latitude, it.longitude) }
        }
    }

    /** Popular cities shown before the user types. */
    suspend fun popular(limit: Int = 24): List<City> {
        val wanted = listOf(
            "London" to "GB", "New York City" to "US", "Tokyo" to "JP", "Paris" to "FR", "Dubai" to "AE",
            "Cairo" to "EG", "Sydney" to "AU", "Los Angeles" to "US", "Singapore" to "SG", "Istanbul" to "TR",
            "Dallas" to "US", "Chicago" to "US", "Hong Kong" to "HK", "Mumbai" to "IN", "Rome" to "IT",
            "Toronto" to "CA", "Mexico City" to "MX", "Sao Paulo" to "BR", "Reykjavik" to "IS", "Cape Town" to "ZA",
            "Seoul" to "KR", "Berlin" to "DE", "Riyadh" to "SA", "Honolulu" to "US",
        )
        val all = ensureLoaded()
        return wanted.mapNotNull { (n, cc) ->
            all.filter { it.countryCode == cc && (it.name.equals(n, true) || normalize(it.name) == normalize(n)) }
                .maxByOrNull { it.population }
        }.take(limit)
    }

    companion object {
        private val marks = "\\p{InCombiningDiacriticalMarks}+".toRegex()

        fun normalize(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(marks, "")
                .replace('’', '\'')

        fun isValidZone(id: String): Boolean = runCatching { ZoneId.of(id) }.isSuccess

        private val US_STATES = mapOf(
            "Alabama" to "AL", "Alaska" to "AK", "Arizona" to "AZ", "Arkansas" to "AR", "California" to "CA",
            "Colorado" to "CO", "Connecticut" to "CT", "Delaware" to "DE", "Florida" to "FL", "Georgia" to "GA",
            "Hawaii" to "HI", "Idaho" to "ID", "Illinois" to "IL", "Indiana" to "IN", "Iowa" to "IA",
            "Kansas" to "KS", "Kentucky" to "KY", "Louisiana" to "LA", "Maine" to "ME", "Maryland" to "MD",
            "Massachusetts" to "MA", "Michigan" to "MI", "Minnesota" to "MN", "Mississippi" to "MS",
            "Missouri" to "MO", "Montana" to "MT", "Nebraska" to "NE", "Nevada" to "NV", "New Hampshire" to "NH",
            "New Jersey" to "NJ", "New Mexico" to "NM", "New York" to "NY", "North Carolina" to "NC",
            "North Dakota" to "ND", "Ohio" to "OH", "Oklahoma" to "OK", "Oregon" to "OR", "Pennsylvania" to "PA",
            "Rhode Island" to "RI", "South Carolina" to "SC", "South Dakota" to "SD", "Tennessee" to "TN",
            "Texas" to "TX", "Utah" to "UT", "Vermont" to "VT", "Virginia" to "VA", "Washington" to "WA",
            "West Virginia" to "WV", "Wisconsin" to "WI", "Wyoming" to "WY", "Washington, D.C." to "DC",
            "District of Columbia" to "DC",
        )

        fun usStateCode(city: City): String = if (city.countryCode == "US") US_STATES[city.admin1] ?: "" else ""
    }
}
