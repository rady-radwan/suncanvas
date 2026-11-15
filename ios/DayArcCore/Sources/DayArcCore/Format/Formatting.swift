import Foundation

/// Formats instants in an explicit time zone (never assumes the phone's zone).
public struct TimeFormatter {
    public let use24h: Bool
    public let dateStyle: DateStyle

    public init(use24h: Bool, dateStyle: DateStyle = .medium) { self.use24h = use24h; self.dateStyle = dateStyle }

    private func f(_ pattern: String, _ zone: TimeZone) -> DateFormatter {
        let key = "\(pattern)|\(zone.identifier)" as NSString
        if let cached = TimeFormatter.cache.object(forKey: key) { return cached }
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = zone
        df.dateFormat = pattern
        TimeFormatter.cache.setObject(df, forKey: key)
        return df
    }
    static let cache = NSCache<NSString, DateFormatter>()

    public func time(_ date: Date?, _ zone: TimeZone, placeholder: String = "—") -> String {
        guard let d = date else { return placeholder }
        return f(use24h ? "HH:mm" : "h:mm a", zone).string(from: d)
    }

    public func clockParts(_ date: Date, _ zone: TimeZone) -> (String, String?) {
        use24h ? (f("HH:mm", zone).string(from: date), nil) : (f("h:mm", zone).string(from: date), f("a", zone).string(from: date))
    }

    public func date(_ date: Date, _ zone: TimeZone) -> String { f(dateStyle.pattern, zone).string(from: date) }
    public func date(_ d: LocalDate) -> String { f(dateStyle.pattern, TimeZone(identifier: "UTC")!).string(from: d.dateAtNoonUTC) }
    public func shortDate(_ d: LocalDate) -> String { f("MMM d", TimeZone(identifier: "UTC")!).string(from: d.dateAtNoonUTC) }
    public func monthYear(_ d: LocalDate) -> String { f("MMMM yyyy", TimeZone(identifier: "UTC")!).string(from: d.dateAtNoonUTC) }
    public func weekday(_ d: LocalDate) -> String { f("EEE", TimeZone(identifier: "UTC")!).string(from: d.dateAtNoonUTC) }

    public func timeWithDay(_ date: Date?, _ zone: TimeZone, reference: LocalDate) -> String {
        guard let d = date else { return "—" }
        let ld = LocalDate(d, in: zone)
        return ld == reference ? time(d, zone) : "\(time(d, zone)) · \(shortDate(ld))"
    }

    public static func abbreviation(_ zone: TimeZone, at date: Date = Date()) -> String {
        if let a = zone.abbreviation(for: date), !a.hasPrefix("GMT") || a == "GMT" { return a }
        return utcOffset(zone, at: date)
    }

    public static func utcOffset(_ zone: TimeZone, at date: Date = Date()) -> String {
        let s = zone.secondsFromGMT(for: date)
        if s == 0 { return "UTC" }
        let sign = s >= 0 ? "+" : "−"
        let h = abs(s) / 3600, m = (abs(s) % 3600) / 60
        return m == 0 ? "UTC\(sign)\(h)" : String(format: "UTC%@%d:%02d", sign, h, m)
    }
}

public enum Durations {
    /// "10h 34m", "45m".
    public static func hm(_ seconds: TimeInterval) -> String {
        let total = Int(abs(seconds) / 60)
        let h = total / 60, m = total % 60
        if h > 0 && m > 0 { return "\(h)h \(m)m" }
        if h > 0 { return "\(h)h" }
        return "\(m)m"
    }

    /// "+2m 05s" / "−1m 38s".
    public static func signedMinSec(_ seconds: TimeInterval) -> String {
        let sign = seconds < 0 ? "−" : "+"
        let s = Int(abs(seconds).rounded())
        return s >= 60 ? "\(sign)\(s / 60)m \(String(format: "%02d", s % 60))s" : "\(sign)\(s)s"
    }

    /// "in 2h 05m", "in 4m", "now".
    public static func countdown(from: Date, to: Date) -> String {
        let d = to.timeIntervalSince(from)
        if d < 30 { return "now" }
        let totalMin = Int((d + 59) / 60)
        let days = totalMin / 1440, h = (totalMin / 60) % 24, m = totalMin % 60
        if days > 0 { return "in \(days)d \(h)h" }
        if h > 0 { return "in \(h)h \(String(format: "%02d", m))m" }
        return "in \(m)m"
    }

    public static func clock(_ seconds: TimeInterval) -> String {
        let s = Int(abs(seconds))
        return String(format: "%d:%02d", s / 3600, (s % 3600) / 60)
    }
}

public func distanceKm(_ lat1: Double, _ lon1: Double, _ lat2: Double, _ lon2: Double) -> Double {
    let r = 6371.0
    let dLat = (lat2 - lat1) * .pi / 180, dLon = (lon2 - lon1) * .pi / 180
    let a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * asin(min(1, sqrt(a)))
}

public func formatCoordinate(_ v: Double, latitude: Bool) -> String {
    let hemi = latitude ? (v >= 0 ? "N" : "S") : (v >= 0 ? "E" : "W")
    return String(format: "%.4f° %@", abs(v), hemi)
}

public func compassPoint(_ az: Double) -> String {
    let dirs = ["N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"]
    let n = (az.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360)
    return dirs[Int(n / 22.5 + 0.5) % 16]
}
