import Foundation

public enum SolarAltitude {
    public static let horizon = NoaaSolarPosition.sunriseAltitude
    public static let civil = -6.0
    public static let nautical = -12.0
    public static let astronomical = -18.0
    /// Photography conventions, not astronomical definitions.
    public static let goldenUpper = 6.0
    public static let goldenLower = -4.0
    public static let blueUpper = -4.0
    public static let blueLower = -6.0
}

public enum DayType: String, Codable { case normal, polarDay, polarNight }

/// A calendar date independent of any time zone.
public struct LocalDate: Hashable, Comparable, Codable, CustomStringConvertible {
    public let year: Int, month: Int, day: Int
    public init(year: Int, month: Int, day: Int) { self.year = year; self.month = month; self.day = day }

    public static func < (a: LocalDate, b: LocalDate) -> Bool { (a.year, a.month, a.day) < (b.year, b.month, b.day) }
    public var description: String { String(format: "%04d-%02d-%02d", year, month, day) }

    static var gregorianUTC: Calendar {
        var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(identifier: "UTC")!; return c
    }

    /// The local date of an instant in a time zone.
    public init(_ date: Date, in zone: TimeZone) {
        var c = Calendar(identifier: .gregorian); c.timeZone = zone
        let comps = c.dateComponents([.year, .month, .day], from: date)
        self.init(year: comps.year!, month: comps.month!, day: comps.day!)
    }

    public func adding(days: Int) -> LocalDate {
        let c = LocalDate.gregorianUTC
        let d = c.date(from: DateComponents(year: year, month: month, day: day))!
        let n = c.date(byAdding: .day, value: days, to: d)!
        let comps = c.dateComponents([.year, .month, .day], from: n)
        return LocalDate(year: comps.year!, month: comps.month!, day: comps.day!)
    }

    /// Local midnight (or the first valid instant of the day) in [zone].
    public func start(in zone: TimeZone) -> Date {
        var c = Calendar(identifier: .gregorian); c.timeZone = zone
        let noon = c.date(from: DateComponents(year: year, month: month, day: day, hour: 12))!
        return c.startOfDay(for: noon)
    }

    public var weekday: Int { // 1 = Monday ... 7 = Sunday
        let c = LocalDate.gregorianUTC
        let w = c.component(.weekday, from: c.date(from: DateComponents(year: year, month: month, day: day))!)
        return w == 1 ? 7 : w - 1
    }

    public var daysInMonth: Int {
        let c = LocalDate.gregorianUTC
        return c.range(of: .day, in: .month, for: c.date(from: DateComponents(year: year, month: month, day: 1))!)!.count
    }

    public var dateAtNoonUTC: Date { LocalDate.gregorianUTC.date(from: DateComponents(year: year, month: month, day: day, hour: 12))! }
}

public struct TimeWindow: Equatable, Codable {
    public let start: Date
    public let end: Date
    public let startClipped: Bool
    public let endClipped: Bool
    public var duration: TimeInterval { end.timeIntervalSince(start) }
    public func contains(_ d: Date) -> Bool { d >= start && d < end }
}

public enum SolarEventType: String, Codable, CaseIterable {
    case astronomicalDawn, nauticalDawn, civilDawn, sunrise, goldenMorningEnd, solarNoon,
         goldenEveningStart, sunset, civilDusk, nauticalDusk, astronomicalDusk, solarMidnight

    public var label: String {
        switch self {
        case .astronomicalDawn: return "Astronomical dawn"
        case .nauticalDawn: return "Nautical dawn"
        case .civilDawn: return "Civil dawn"
        case .sunrise: return "Sunrise"
        case .goldenMorningEnd: return "Morning golden hour ends"
        case .solarNoon: return "Solar noon"
        case .goldenEveningStart: return "Evening golden hour starts"
        case .sunset: return "Sunset"
        case .civilDusk: return "Civil dusk"
        case .nauticalDusk: return "Nautical dusk"
        case .astronomicalDusk: return "Astronomical dusk"
        case .solarMidnight: return "Solar midnight"
        }
    }
}

public struct SolarEvent: Equatable { public let type: SolarEventType; public let date: Date }

public struct SolarDay: Equatable {
    public let date: LocalDate
    public let zone: TimeZone
    public let latitude: Double
    public let longitude: Double
    public let dayType: DayType
    public let sunrise: Date?
    public let sunset: Date?
    public let solarNoon: Date?
    public let noonElevation: Double
    public let solarMidnight: Date?
    public let civilDawn: Date?, civilDusk: Date?
    public let nauticalDawn: Date?, nauticalDusk: Date?
    public let astronomicalDawn: Date?, astronomicalDusk: Date?
    /// Seconds with the Sun above the horizon on this local date.
    public let daylight: TimeInterval
    public let goldenMorning: TimeWindow?, goldenEvening: TimeWindow?
    public let blueMorning: TimeWindow?, blueEvening: TimeWindow?
    /// Length of the local date (23 h / 25 h on DST days).
    public let dayLength: TimeInterval

    public var events: [SolarEvent] {
        var e: [SolarEvent] = []
        func add(_ t: SolarEventType, _ d: Date?) { if let d = d { e.append(SolarEvent(type: t, date: d)) } }
        add(.astronomicalDawn, astronomicalDawn); add(.nauticalDawn, nauticalDawn); add(.civilDawn, civilDawn)
        add(.sunrise, sunrise)
        if let g = goldenMorning, !g.endClipped { add(.goldenMorningEnd, g.end) }
        add(.solarNoon, solarNoon)
        if let g = goldenEvening, !g.startClipped { add(.goldenEveningStart, g.start) }
        add(.sunset, sunset); add(.civilDusk, civilDusk); add(.nauticalDusk, nauticalDusk)
        add(.astronomicalDusk, astronomicalDusk); add(.solarMidnight, solarMidnight)
        return e.sorted { $0.date < $1.date }
    }
}

public struct NextSunEvents: Equatable {
    public let nextSunrise: Date?
    public let nextSunset: Date?
    public var nextIsSunrise: Bool? {
        switch (nextSunrise, nextSunset) {
        case (nil, nil): return nil
        case (nil, _): return false
        case (_, nil): return true
        case let (r?, s?): return r < s
        }
    }
}

/// Finds all daily solar events for a local calendar date in the location's own time zone by sampling
/// the geometric elevation every 5 minutes and refining each crossing by bisection (< 0.25 s).
/// Robust for polar day/night, DST days (23/25 h) and the International Date Line.
public struct SolarCalculator {
    let stepMs: Int64

    public init(stepMinutes: Int = 5) { stepMs = Int64(stepMinutes) * 60_000 }

    public func position(_ date: Date, latitude: Double, longitude: Double) -> SunPosition {
        NoaaSolarPosition.position(date.ms, latitude: latitude, longitude: longitude)
    }

    public func day(_ date: LocalDate, latitude: Double, longitude: Double, zone: TimeZone) -> SolarDay {
        let lat = max(-89.9999, min(89.9999, latitude))
        let start = date.start(in: zone).ms
        let end = date.adding(days: 1).start(in: zone).ms
        let s = Sampler(start: start, end: end, lat: lat, lon: longitude, step: stepMs)

        let horizon = s.crossings(SolarAltitude.horizon)
        let civil = s.crossings(SolarAltitude.civil)
        let nautical = s.crossings(SolarAltitude.nautical)
        let astro = s.crossings(SolarAltitude.astronomical)

        let dayType: DayType
        if !horizon.isEmpty { dayType = .normal }
        else if s.elevations.allSatisfy({ $0 > SolarAltitude.horizon }) { dayType = .polarDay }
        else { dayType = .polarNight }

        let noon = s.hourAngleZero(shifted: false)
        let midnight = s.hourAngleZero(shifted: true)
        let noonMs = noon ?? (start + end) / 2
        let noonEl = NoaaSolarPosition.position(noonMs, latitude: lat, longitude: longitude).elevation
        let daylightMs = s.bands(lo: SolarAltitude.horizon, hi: .infinity).reduce(Int64(0)) { $0 + ($1.1 - $1.0) }
        let golden = s.bands(lo: SolarAltitude.goldenLower, hi: SolarAltitude.goldenUpper)
        let blue = s.bands(lo: SolarAltitude.blueLower, hi: SolarAltitude.blueUpper)

        func pick(_ bands: [(Int64, Int64)], morning: Bool) -> TimeWindow? {
            let c = bands.filter { b in let mid = (b.0 + b.1) / 2; return morning ? mid <= noonMs : mid > noonMs }
            guard let b = morning ? c.first : c.last else { return nil }
            return TimeWindow(start: Date(ms: b.0), end: Date(ms: b.1), startClipped: b.0 <= start, endClipped: b.1 >= end)
        }
        func first(_ c: [Crossing], rising: Bool) -> Date? { c.first(where: { $0.rising == rising }).map { Date(ms: $0.time) } }
        func last(_ c: [Crossing], rising: Bool) -> Date? { c.last(where: { $0.rising == rising }).map { Date(ms: $0.time) } }

        return SolarDay(
            date: date, zone: zone, latitude: latitude, longitude: longitude, dayType: dayType,
            sunrise: first(horizon, rising: true), sunset: last(horizon, rising: false),
            solarNoon: noon.map { Date(ms: $0) }, noonElevation: noonEl, solarMidnight: midnight.map { Date(ms: $0) },
            civilDawn: first(civil, rising: true), civilDusk: last(civil, rising: false),
            nauticalDawn: first(nautical, rising: true), nauticalDusk: last(nautical, rising: false),
            astronomicalDawn: first(astro, rising: true), astronomicalDusk: last(astro, rising: false),
            daylight: Double(daylightMs) / 1000,
            goldenMorning: pick(golden, morning: true), goldenEvening: pick(golden, morning: false),
            blueMorning: pick(blue, morning: true), blueEvening: pick(blue, morning: false),
            dayLength: Double(end - start) / 1000
        )
    }

    /// Apparent elevation samples across a local day.
    public func elevationCurve(_ date: LocalDate, latitude: Double, longitude: Double, zone: TimeZone, points: Int = 97) -> [(Date, Double)] {
        let start = date.start(in: zone).ms
        let end = date.adding(days: 1).start(in: zone).ms
        let n = max(points, 2)
        return (0..<n).map { i in
            let t = start + (end - start) * Int64(i) / Int64(n - 1)
            return (Date(ms: t), NoaaSolarPosition.position(t, latitude: latitude, longitude: longitude).elevation)
        }
    }

    /// Next sunrise and sunset strictly after [from], up to [maxDays] days ahead (polar nights can be long).
    public func nextSunEvents(from: Date, latitude: Double, longitude: Double, zone: TimeZone, maxDays: Int = 370) -> NextSunEvents {
        var rise: Date? = nil, set: Date? = nil
        var d = LocalDate(from, in: zone)
        var i = 0
        let fromMs = from.ms
        let lat = max(-89.9999, min(89.9999, latitude))
        while i < maxDays && (rise == nil || set == nil) {
            let s = Sampler(start: d.start(in: zone).ms, end: d.adding(days: 1).start(in: zone).ms, lat: lat, lon: longitude, step: stepMs)
            for c in s.crossings(SolarAltitude.horizon) where c.time > fromMs {
                if c.rising && rise == nil { rise = Date(ms: c.time) }
                if !c.rising && set == nil { set = Date(ms: c.time) }
            }
            d = d.adding(days: 1); i += 1
        }
        return NextSunEvents(nextSunrise: rise, nextSunset: set)
    }

    struct Crossing { let time: Int64; let rising: Bool }

    struct Sampler {
        let start: Int64, end: Int64, lat: Double, lon: Double
        let times: [Int64]
        let elevations: [Double]

        init(start: Int64, end: Int64, lat: Double, lon: Double, step: Int64) {
            self.start = start; self.end = end; self.lat = lat; self.lon = lon
            let n = Int((end - start + step - 1) / step) + 1
            let ts = (0..<n).map { min(start + Int64($0) * step, end) }
            times = ts
            elevations = ts.map { NoaaSolarPosition.geometricElevation($0, latitude: lat, longitude: lon) }
        }

        func elev(_ t: Int64) -> Double { NoaaSolarPosition.geometricElevation(t, latitude: lat, longitude: lon) }

        func crossings(_ h: Double) -> [Crossing] {
            var out: [Crossing] = []
            for i in 0..<(times.count - 1) where times[i + 1] != times[i] {
                let a = elevations[i] - h, b = elevations[i + 1] - h
                if a < 0 && b >= 0 { out.append(Crossing(time: refine(times[i], times[i + 1], h, rising: true), rising: true)) }
                else if a >= 0 && b < 0 { out.append(Crossing(time: refine(times[i], times[i + 1], h, rising: false), rising: false)) }
            }
            return out
        }

        func refine(_ t0: Int64, _ t1: Int64, _ h: Double, rising: Bool) -> Int64 {
            var lo = t0, hi = t1
            while hi - lo > 250 {
                let mid = (lo + hi) / 2
                if (elev(mid) >= h) == rising { hi = mid } else { lo = mid }
            }
            return (lo + hi) / 2
        }

        /// Intervals where lo <= elevation < hi.
        func bands(lo: Double, hi: Double) -> [(Int64, Int64)] {
            var bounds: [Int64] = [start]
            bounds += crossings(lo).map { $0.time }
            if hi.isFinite { bounds += crossings(hi).map { $0.time } }
            bounds.append(end)
            bounds.sort()
            var result: [(Int64, Int64)] = []
            for i in 0..<(bounds.count - 1) {
                let a = bounds[i], b = bounds[i + 1]
                if b <= a { continue }
                let e = elev((a + b) / 2)
                if e >= lo && e < hi {
                    if let last = result.last, last.1 == a { result[result.count - 1] = (last.0, b) } else { result.append((a, b)) }
                }
            }
            return result
        }

        func hourAngleZero(shifted: Bool) -> Int64? {
            func ha(_ t: Int64) -> Double {
                let raw = NoaaSolarPosition.hourAngle(t, longitude: lon)
                if !shifted { return raw }
                var v = raw + 180; if v >= 180 { v -= 360 }; return v
            }
            var prev = ha(times[0])
            for i in 1..<times.count {
                let cur = ha(times[i])
                if prev < 0 && cur >= 0 && cur - prev < 90 {
                    var lo = times[i - 1], hi = times[i]
                    while hi - lo > 250 { let mid = (lo + hi) / 2; if ha(mid) >= 0 { hi = mid } else { lo = mid } }
                    return (lo + hi) / 2
                }
                prev = cur
            }
            return nil
        }
    }
}

public enum SeasonEventType: String, CaseIterable {
    case marchEquinox, juneSolstice, septemberEquinox, decemberSolstice
    public var label: String {
        switch self {
        case .marchEquinox: return "March equinox"
        case .juneSolstice: return "June solstice"
        case .septemberEquinox: return "September equinox"
        case .decemberSolstice: return "December solstice"
        }
    }
}

public struct SeasonEvent: Equatable { public let type: SeasonEventType; public let date: Date }

/// Equinoxes and solstices by bisection on the Sun's apparent longitude.
public enum Seasons {
    public static func events(year: Int) -> [SeasonEvent] {
        [find(year, 3, 20, 0, .marchEquinox), find(year, 6, 21, 90, .juneSolstice),
         find(year, 9, 22, 180, .septemberEquinox), find(year, 12, 21, 270, .decemberSolstice)]
    }

    public static func upcoming(from: Date, count: Int = 4) -> [SeasonEvent] {
        let y = Calendar(identifier: .gregorian).dateComponents(in: TimeZone(identifier: "UTC")!, from: from).year!
        return Array((events(year: y) + events(year: y + 1) + events(year: y + 2)).filter { $0.date >= from }.prefix(count))
    }

    static func find(_ year: Int, _ month: Int, _ day: Int, _ target: Double, _ type: SeasonEventType) -> SeasonEvent {
        let guess = LocalDate(year: year, month: month, day: day).start(in: TimeZone(identifier: "UTC")!).ms
        let hour: Int64 = 3_600_000
        var lo = guess - 6 * 24 * hour, hi = guess + 6 * 24 * hour
        func f(_ t: Int64) -> Double {
            var d = NoaaSolarPosition.ephemeris(t).apparentLongitude - target
            while d > 180 { d -= 360 }
            while d <= -180 { d += 360 }
            return d
        }
        while hi - lo > 1000 { let mid = (lo + hi) / 2; if f(mid) >= 0 { hi = mid } else { lo = mid } }
        return SeasonEvent(type: type, date: Date(ms: (lo + hi) / 2))
    }
}

public extension Date {
    var ms: Int64 { Int64((timeIntervalSince1970 * 1000).rounded()) }
    init(ms: Int64) { self.init(timeIntervalSince1970: Double(ms) / 1000) }
}
