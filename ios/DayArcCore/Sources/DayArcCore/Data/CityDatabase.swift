import Foundation

public struct City: Identifiable, Hashable, Codable {
    public var id: Int { geonameId }
    public let geonameId: Int
    public let name: String
    public let admin1: String
    public let countryCode: String
    public let countryName: String
    public let latitude: Double
    public let longitude: Double
    public let population: Int
    public let timeZoneId: String
    public var alternates: [String] = []
    public var fromOnline = false

    public var region: String { [admin1, countryName].filter { !$0.isEmpty }.joined(separator: ", ") }

    public func toLocation() -> SavedLocation {
        SavedLocation(name: name, latitude: latitude, longitude: longitude, timeZoneId: timeZoneId,
                      source: fromOnline ? .online : .city, admin1: admin1, countryCode: countryCode,
                      countryName: countryName, geonameId: geonameId)
    }
}

/// Offline world city database (~34 000 places, GeoNames CC-BY 4.0) bundled as TSV.
/// The full list is loaded lazily for the app; widgets use the streaming search to stay small.
public final class CityDatabase {
    public static let shared = CityDatabase()

    private let lock = NSLock()
    private var cities: [City]? = nil
    private var keys: [String] = []
    private lazy var countries: [String: String] = Self.loadCountries()

    static var bundle: Bundle { Bundle.module }

    static func loadCountries() -> [String: String] {
        guard let url = bundle.url(forResource: "countries", withExtension: "tsv"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return [:] }
        var map: [String: String] = [:]
        for line in text.split(separator: "\n") {
            let p = line.split(separator: "\t", maxSplits: 1)
            if p.count == 2 { map[String(p[0])] = String(p[1]) }
        }
        return map
    }

    public func countryName(_ code: String) -> String { countries[code] ?? code }

    static func parse(_ line: Substring, countries: [String: String]) -> City? {
        if line.hasPrefix("#") { return nil }
        let c = line.split(separator: "\t", omittingEmptySubsequences: false)
        guard c.count >= 8, let id = Int(c[0]), let lat = Double(c[4]), let lon = Double(c[5]) else { return nil }
        let cc = String(c[3])
        return City(geonameId: id, name: String(c[1]), admin1: String(c[2]), countryCode: cc, countryName: countries[cc] ?? cc,
                    latitude: lat, longitude: lon, population: Int(c[6]) ?? 0, timeZoneId: String(c[7]),
                    alternates: c.count > 8 && !c[8].isEmpty ? c[8].split(separator: "|").map(String.init) : [])
    }

    public func all() -> [City] {
        lock.lock(); defer { lock.unlock() }
        if let c = cities { return c }
        guard let url = Self.bundle.url(forResource: "cities", withExtension: "tsv"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { cities = []; return [] }
        let cs = countries
        var list: [City] = []
        list.reserveCapacity(34_500)
        for line in text.split(separator: "\n") { if let city = Self.parse(line, countries: cs) { list.append(city) } }
        keys = list.map { Self.normalize($0.name) }
        cities = list
        return list
    }

    public static func normalize(_ s: String) -> String {
        s.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "en_US")).replacingOccurrences(of: "’", with: "'")
    }

    /// "paris", "paris, tx", "paris texas", "paris france". Exact → prefix → word prefix → alternates; ties by population.
    public func search(_ query: String, limit: Int = 40) -> [City] {
        let list = all()
        let raw = Self.normalize(query).trimmingCharacters(in: .whitespaces)
        guard raw.count >= 2 else { return [] }
        let parts = raw.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        guard var name = parts.first else { return [] }
        var qualifier = parts.dropFirst().joined(separator: " ")
        var results = rank(list, name: name, qualifier: qualifier, limit: limit)
        if results.isEmpty && parts.count == 1, let space = name.lastIndex(of: " ") {
            qualifier = String(name[name.index(after: space)...])
            name = String(name[..<space])
            results = rank(list, name: name, qualifier: qualifier, limit: limit)
        }
        return results
    }

    private func rank(_ list: [City], name: String, qualifier: String, limit: Int) -> [City] {
        var hits: [(City, Int)] = []
        for (i, city) in list.enumerated() {
            let key = keys[i]
            let tier: Int
            if key == name { tier = 0 }
            else if key.hasPrefix(name) { tier = 1 }
            else if key.contains(" " + name) || key.contains("-" + name) { tier = 2 }
            else if city.alternates.contains(where: { Self.normalize($0).hasPrefix(name) }) { tier = 3 }
            else { continue }
            if !qualifier.isEmpty && !Self.matches(city, qualifier) { continue }
            hits.append((city, tier))
        }
        return hits.sorted { $0.1 != $1.1 ? $0.1 < $1.1 : $0.0.population > $1.0.population }.prefix(limit).map { $0.0 }
    }

    static func matches(_ city: City, _ q: String) -> Bool {
        normalize(city.admin1).hasPrefix(q) || normalize(city.countryName).hasPrefix(q) ||
            city.countryCode.lowercased() == q || usStateCode(city).lowercased() == q
    }

    /// Lightweight search that streams the file without keeping it in memory (for widget configuration).
    public static func streamSearch(_ query: String, limit: Int = 25) -> [City] {
        let q = normalize(query).trimmingCharacters(in: .whitespaces)
        guard q.count >= 2, let url = bundle.url(forResource: "cities", withExtension: "tsv"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return [] }
        let countries = loadCountries()
        var hits: [(City, Int)] = []
        for line in text.split(separator: "\n") {
            let tab = line.firstIndex(of: "\t") ?? line.startIndex
            let rest = line[line.index(after: tab)...]
            let nameEnd = rest.firstIndex(of: "\t") ?? rest.endIndex
            let key = normalize(String(rest[..<nameEnd]))
            let tier = key == q ? 0 : key.hasPrefix(q) ? 1 : -1
            if tier < 0 { continue }
            if let c = parse(line, countries: countries) { hits.append((c, tier)) }
        }
        return hits.sorted { $0.1 != $1.1 ? $0.1 < $1.1 : $0.0.population > $1.0.population }.prefix(limit).map { $0.0 }
    }

    /// Nearest city and distance (km).
    public func nearest(latitude: Double, longitude: Double) -> (City, Double)? {
        var best: City? = nil
        var bestScore = Double.greatestFiniteMagnitude
        let cosLat = cos(latitude * .pi / 180)
        for c in all() {
            let dLat = c.latitude - latitude
            var dLon = c.longitude - longitude
            if dLon > 180 { dLon -= 360 }
            if dLon < -180 { dLon += 360 }
            let s = dLat * dLat + (dLon * cosLat) * (dLon * cosLat)
            if s < bestScore { bestScore = s; best = c }
        }
        return best.map { ($0, distanceKm(latitude, longitude, $0.latitude, $0.longitude)) }
    }

    public func popular() -> [City] {
        let wanted = [("London", "GB"), ("New York City", "US"), ("Tokyo", "JP"), ("Paris", "FR"), ("Dubai", "AE"), ("Cairo", "EG"),
                      ("Sydney", "AU"), ("Los Angeles", "US"), ("Singapore", "SG"), ("Istanbul", "TR"), ("Dallas", "US"), ("Chicago", "US"),
                      ("Hong Kong", "HK"), ("Mumbai", "IN"), ("Rome", "IT"), ("Toronto", "CA"), ("Mexico City", "MX"), ("Reykjavik", "IS"),
                      ("Cape Town", "ZA"), ("Seoul", "KR"), ("Berlin", "DE"), ("Riyadh", "SA"), ("Honolulu", "US")]
        let list = all()
        return wanted.compactMap { (n, cc) in
            list.filter { $0.countryCode == cc && Self.normalize($0.name) == Self.normalize(n) }.max { $0.population < $1.population }
        }
    }

    static let usStates: [String: String] = [
        "Alabama": "AL", "Alaska": "AK", "Arizona": "AZ", "Arkansas": "AR", "California": "CA", "Colorado": "CO", "Connecticut": "CT",
        "Delaware": "DE", "Florida": "FL", "Georgia": "GA", "Hawaii": "HI", "Idaho": "ID", "Illinois": "IL", "Indiana": "IN", "Iowa": "IA",
        "Kansas": "KS", "Kentucky": "KY", "Louisiana": "LA", "Maine": "ME", "Maryland": "MD", "Massachusetts": "MA", "Michigan": "MI",
        "Minnesota": "MN", "Mississippi": "MS", "Missouri": "MO", "Montana": "MT", "Nebraska": "NE", "Nevada": "NV", "New Hampshire": "NH",
        "New Jersey": "NJ", "New Mexico": "NM", "New York": "NY", "North Carolina": "NC", "North Dakota": "ND", "Ohio": "OH", "Oklahoma": "OK",
        "Oregon": "OR", "Pennsylvania": "PA", "Rhode Island": "RI", "South Carolina": "SC", "South Dakota": "SD", "Tennessee": "TN",
        "Texas": "TX", "Utah": "UT", "Vermont": "VT", "Virginia": "VA", "Washington": "WA", "West Virginia": "WV", "Wisconsin": "WI",
        "Wyoming": "WY", "Washington, D.C.": "DC", "District of Columbia": "DC",
    ]
    static func usStateCode(_ c: City) -> String { c.countryCode == "US" ? (usStates[c.admin1] ?? "") : "" }
}

public struct ResolvedPlace {
    public let name: String
    public let admin1: String
    public let countryCode: String
    public let countryName: String
    public let timeZoneId: String
    public let nearestCity: City?
    public let distanceKm: Double?
    public let confident: Bool
}

/// Offline coordinates → IANA zone + place name, from the nearest bundled city (≤ 400 km),
/// otherwise the nautical zone Etc/GMT±n.
public enum TimeZoneResolver {
    public static func resolve(latitude: Double, longitude: Double) -> ResolvedPlace {
        let near = CityDatabase.shared.nearest(latitude: latitude, longitude: longitude)
        if let n = near, n.1 <= 400 {
            let city = n.0, dist = n.1
            return ResolvedPlace(name: dist <= 25 ? city.name : "Near \(city.name)", admin1: city.admin1, countryCode: city.countryCode,
                                 countryName: city.countryName, timeZoneId: city.timeZoneId, nearestCity: city, distanceKm: dist, confident: true)
        }
        return ResolvedPlace(name: String(format: "%.2f, %.2f", latitude, longitude), admin1: "", countryCode: near?.0.countryCode ?? "",
                             countryName: near?.0.countryName ?? "", timeZoneId: nauticalZone(longitude), nearestCity: near?.0,
                             distanceKm: near?.1, confident: false)
    }

    /// Etc/GMT zones have inverted signs: Etc/GMT-3 is UTC+3.
    public static func nauticalZone(_ longitude: Double) -> String {
        let o = max(-12, min(12, Int((longitude / 15).rounded())))
        let id = o == 0 ? "Etc/GMT" : o > 0 ? "Etc/GMT-\(o)" : "Etc/GMT+\(-o)"
        return TimeZone(identifier: id) != nil ? id : "UTC"
    }
}
