import Foundation

public enum LocationSource: String, Codable { case device = "DEVICE", city = "CITY", online = "ONLINE", manual = "MANUAL" }

public struct SavedLocation: Codable, Identifiable, Hashable {
    public var id: String = UUID().uuidString
    public var name: String
    public var customLabel: String? = nil
    public var admin1: String = ""
    public var countryCode: String = ""
    public var countryName: String = ""
    public var latitude: Double
    public var longitude: Double
    public var timeZoneId: String
    public var source: LocationSource = .city
    public var geonameId: Int? = nil
    public var isFavorite = false
    public var isDefault = false
    public var inCityList = true
    public var sortOrder = 0

    public init(name: String, latitude: Double, longitude: Double, timeZoneId: String, source: LocationSource = .city,
                admin1: String = "", countryCode: String = "", countryName: String = "", geonameId: Int? = nil) {
        self.name = name; self.latitude = latitude; self.longitude = longitude; self.timeZoneId = timeZoneId
        self.source = source; self.admin1 = admin1; self.countryCode = countryCode; self.countryName = countryName; self.geonameId = geonameId
    }

    public var zone: TimeZone { TimeZone(identifier: timeZoneId) ?? TimeZone(identifier: "UTC")! }
    public var displayName: String { (customLabel?.isEmpty == false ? customLabel! : name) }
    public var subtitle: String {
        if countryCode == "US" && !admin1.isEmpty { return "\(admin1), \(countryName.isEmpty ? "USA" : countryName)" }
        if !admin1.isEmpty && !countryName.isEmpty { return "\(admin1), \(countryName)" }
        return countryName
    }
    public var headline: String {
        if let l = customLabel, !l.isEmpty { return l }
        if countryCode == "US" && !admin1.isEmpty { return "\(name), \(admin1)" }
        return countryName.isEmpty ? name : "\(name), \(countryName)"
    }
}

public enum ThemeMode: String, Codable, CaseIterable, Identifiable {
    case system = "SYSTEM", light = "LIGHT", dark = "DARK", sun = "SUN"
    public var id: String { rawValue }
    public var label: String {
        switch self { case .system: return "Follow system"; case .light: return "Light"; case .dark: return "Dark"; case .sun: return "Automatic — sunrise & sunset" }
    }
}

public enum TimeFormatPref: String, Codable, CaseIterable, Identifiable {
    case system = "SYSTEM", h12 = "H12", h24 = "H24"
    public var id: String { rawValue }
    public var label: String { switch self { case .system: return "Follow system"; case .h12: return "12-hour"; case .h24: return "24-hour" } }
}

public enum DateStyle: String, Codable, CaseIterable, Identifiable {
    case medium = "MEDIUM", long = "LONG", numericUS = "NUMERIC_US", numericEU = "NUMERIC_EU", iso = "ISO"
    public var id: String { rawValue }
    public var pattern: String {
        switch self { case .medium: return "EEE, MMM d"; case .long: return "EEEE, MMMM d"; case .numericUS: return "MM/dd/yyyy"; case .numericEU: return "dd/MM/yyyy"; case .iso: return "yyyy-MM-dd" }
    }
    public var label: String {
        switch self { case .medium: return "Sun, Nov 8"; case .long: return "Sunday, November 8"; case .numericUS: return "11/08/2026"; case .numericEU: return "08/11/2026"; case .iso: return "2026-11-08" }
    }
}

public enum ReminderEvent: String, Codable, CaseIterable, Identifiable {
    case sunrise = "SUNRISE", sunset = "SUNSET", goldenMorningStart = "GOLDEN_MORNING_START", goldenMorningEnd = "GOLDEN_MORNING_END",
         goldenEveningStart = "GOLDEN_EVENING_START", goldenEveningEnd = "GOLDEN_EVENING_END", blueEveningStart = "BLUE_EVENING_START",
         civilDawn = "CIVIL_DAWN", solarNoon = "SOLAR_NOON"
    public var id: String { rawValue }
    public var label: String {
        switch self {
        case .sunrise: return "Sunrise"
        case .sunset: return "Sunset"
        case .goldenMorningStart: return "Morning golden hour starts"
        case .goldenMorningEnd: return "Morning golden hour ends"
        case .goldenEveningStart: return "Evening golden hour starts"
        case .goldenEveningEnd: return "Evening golden hour ends"
        case .blueEveningStart: return "Evening blue hour starts"
        case .civilDawn: return "Civil dawn"
        case .solarNoon: return "Solar noon"
        }
    }
    public func instant(in day: SolarDay) -> Date? {
        switch self {
        case .sunrise: return day.sunrise
        case .sunset: return day.sunset
        case .goldenMorningStart: return day.goldenMorning.flatMap { $0.startClipped ? nil : $0.start }
        case .goldenMorningEnd: return day.goldenMorning.flatMap { $0.endClipped ? nil : $0.end }
        case .goldenEveningStart: return day.goldenEvening.flatMap { $0.startClipped ? nil : $0.start }
        case .goldenEveningEnd: return day.goldenEvening.flatMap { $0.endClipped ? nil : $0.end }
        case .blueEveningStart: return day.blueEvening.flatMap { $0.startClipped ? nil : $0.start }
        case .civilDawn: return day.civilDawn
        case .solarNoon: return day.solarNoon
        }
    }
}

public struct Reminder: Codable, Identifiable, Hashable {
    public var id: String = UUID().uuidString
    public var event: ReminderEvent
    /// Minutes relative to the event; negative = before.
    public var offsetMinutes: Int
    public var enabled = true
    public init(event: ReminderEvent, offsetMinutes: Int) { self.event = event; self.offsetMinutes = offsetMinutes }
    public var summary: String {
        let m = abs(offsetMinutes)
        if offsetMinutes == 0 { return "At \(event.label.lowercased())" }
        return offsetMinutes < 0 ? "\(m) min before \(event.label.lowercased())" : "\(m) min after \(event.label.lowercased())"
    }
}

public enum ClockStyle: String, Codable { case digital = "DIGITAL", analog = "ANALOG" }

public struct AppSettings: Codable, Equatable {
    public var themeMode: ThemeMode = .system
    public var timeFormat: TimeFormatPref = .system
    public var dateStyle: DateStyle = .medium
    public var activeLocationId: String? = nil
    public var onboardingDone = false
    public var onlineSearch = true
    public var worldClockStyle: ClockStyle = .digital
    public var worldClockHidden: [String] = []
    public var notificationsEnabled = false
    public var showSeconds = false
    public init() {}

    enum CodingKeys: String, CodingKey { case themeMode, timeFormat, dateStyle, activeLocationId, onboardingDone, onlineSearch, worldClockStyle, worldClockHidden, notificationsEnabled, showSeconds }
    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        themeMode = (try? c.decode(ThemeMode.self, forKey: .themeMode)) ?? .system
        timeFormat = (try? c.decode(TimeFormatPref.self, forKey: .timeFormat)) ?? .system
        dateStyle = (try? c.decode(DateStyle.self, forKey: .dateStyle)) ?? .medium
        activeLocationId = try? c.decode(String.self, forKey: .activeLocationId)
        onboardingDone = (try? c.decode(Bool.self, forKey: .onboardingDone)) ?? false
        onlineSearch = (try? c.decode(Bool.self, forKey: .onlineSearch)) ?? true
        worldClockStyle = (try? c.decode(ClockStyle.self, forKey: .worldClockStyle)) ?? .digital
        worldClockHidden = (try? c.decode([String].self, forKey: .worldClockHidden)) ?? []
        notificationsEnabled = (try? c.decode(Bool.self, forKey: .notificationsEnabled)) ?? false
        showSeconds = (try? c.decode(Bool.self, forKey: .showSeconds)) ?? false
    }

    public var use24h: Bool {
        switch timeFormat {
        case .h12: return false
        case .h24: return true
        case .system:
            let f = DateFormatter.dateFormat(fromTemplate: "j", options: 0, locale: .current) ?? "h"
            return !f.contains("a")
        }
    }

    public var formatter: TimeFormatter { TimeFormatter(use24h: use24h, dateStyle: dateStyle) }
}

/// A saved Widget Studio design.
public struct SavedDesign: Codable, Identifiable, Hashable {
    public var id: String = UUID().uuidString
    public var spec: WidgetSpec
    public var updatedAt = Date()
    public init(spec: WidgetSpec) { self.spec = spec }
}
