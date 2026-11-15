import Foundation

/// Everything the UI and widgets need about one location at one moment.
public struct SunSnapshot {
    public let location: SavedLocation
    public let zone: TimeZone
    public let now: Date
    public let localDate: LocalDate
    public let today: SolarDay
    public let yesterday: SolarDay
    public let tomorrow: SolarDay
    public let next: NextSunEvents
    public let position: SunPosition

    public var isDay: Bool { position.geometricElevation > SolarAltitude.horizon }
    public var daylightChange: TimeInterval { today.daylight - yesterday.daylight }

    /// Fraction of today's daylight that has passed, or nil at night.
    public var daylightProgress: Double? {
        if today.dayType == .polarDay {
            return min(1, max(0, now.timeIntervalSince(localDate.start(in: zone)) / today.dayLength))
        }
        guard let r = today.sunrise, let s = today.sunset, s > r, now >= r, now <= s else { return nil }
        return min(1, max(0, now.timeIntervalSince(r) / s.timeIntervalSince(r)))
    }

    /// Fraction of the current night that has passed.
    public var nightProgress: Double? {
        guard let prev = [yesterday.sunset, today.sunset].compactMap({ $0 }).filter({ $0 < now }).max(),
              let rise = next.nextSunrise else { return nil }
        let total = rise.timeIntervalSince(prev)
        return total > 0 ? min(1, max(0, now.timeIntervalSince(prev) / total)) : nil
    }

    public var remainingDaylight: TimeInterval? { isDay ? next.nextSunset.map { $0.timeIntervalSince(now) } : nil }

    public var nextSunEvent: SolarEvent? {
        switch next.nextIsSunrise {
        case true?: return SolarEvent(type: .sunrise, date: next.nextSunrise!)
        case false?: return SolarEvent(type: .sunset, date: next.nextSunset!)
        case nil: return nil
        }
    }

    public var nextGoldenHour: TimeWindow? {
        [today.goldenMorning, today.goldenEvening, tomorrow.goldenMorning, tomorrow.goldenEvening].compactMap { $0 }.first { $0.end > now }
    }

    public var inGoldenHour: Bool { nextGoldenHour?.contains(now) == true }
}

/// Memory-cached solar computations.
public final class SolarService {
    public static let shared = SolarService()
    private let calc = SolarCalculator()
    private let cache = NSCache<NSString, Box>()
    final class Box { let day: SolarDay; init(_ d: SolarDay) { day = d } }

    public func day(_ l: SavedLocation, _ date: LocalDate) -> SolarDay {
        day(latitude: l.latitude, longitude: l.longitude, zone: l.zone, date: date)
    }

    public func day(latitude: Double, longitude: Double, zone: TimeZone, date: LocalDate) -> SolarDay {
        let key = String(format: "%.4f|%.4f|%@|%@", latitude, longitude, zone.identifier, date.description) as NSString
        if let b = cache.object(forKey: key) { return b.day }
        let d = calc.day(date, latitude: latitude, longitude: longitude, zone: zone)
        cache.setObject(Box(d), forKey: key)
        return d
    }

    public func range(_ l: SavedLocation, from: LocalDate, days: Int) -> [SolarDay] {
        (0..<days).map { day(l, from.adding(days: $0)) }
    }

    public func position(_ l: SavedLocation, at date: Date) -> SunPosition {
        calc.position(date, latitude: l.latitude, longitude: l.longitude)
    }

    public func curve(_ l: SavedLocation, _ date: LocalDate, points: Int = 97) -> [(Date, Double)] {
        calc.elevationCurve(date, latitude: l.latitude, longitude: l.longitude, zone: l.zone, points: points)
    }

    public func snapshot(_ l: SavedLocation, at now: Date = Date()) -> SunSnapshot {
        let zone = l.zone
        let date = LocalDate(now, in: zone)
        let today = day(l, date)
        let tomorrow = day(l, date.adding(days: 1))
        let rise = [today.sunrise, tomorrow.sunrise].compactMap { $0 }.first { $0 > now }
        let set = [today.sunset, tomorrow.sunset].compactMap { $0 }.first { $0 > now }
        let next: NextSunEvents
        if rise != nil && set != nil {
            next = NextSunEvents(nextSunrise: rise, nextSunset: set)
        } else {
            let far = calc.nextSunEvents(from: now, latitude: l.latitude, longitude: l.longitude, zone: zone)
            next = NextSunEvents(nextSunrise: rise ?? far.nextSunrise, nextSunset: set ?? far.nextSunset)
        }
        return SunSnapshot(location: l, zone: zone, now: now, localDate: date, today: today,
                           yesterday: day(l, date.adding(days: -1)), tomorrow: tomorrow, next: next,
                           position: position(l, at: now))
    }
}
