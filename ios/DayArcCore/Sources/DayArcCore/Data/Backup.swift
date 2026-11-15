import Foundation

/// Backup format shared with the Android app ("dayarc-backup"), so cities, designs and reminders move
/// between devices.
public struct BackupFile: Codable {
    public var format = "dayarc-backup"
    public var version = 1
    public var exportedAt = Int64(Date().timeIntervalSince1970 * 1000)
    public var locations: [BackupLocation] = []
    public var designs: [BackupDesign] = []
    public var widgets: [BackupWidget] = []
    public var reminders: [BackupReminder] = []
    public var settings: [String: String] = [:]

    public init() {}
    enum CodingKeys: String, CodingKey { case format, version, exportedAt, locations, designs, widgets, reminders, settings }
    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        format = (try? c.decode(String.self, forKey: .format)) ?? ""
        version = (try? c.decode(Int.self, forKey: .version)) ?? 1
        exportedAt = (try? c.decode(Int64.self, forKey: .exportedAt)) ?? 0
        locations = (try? c.decode([BackupLocation].self, forKey: .locations)) ?? []
        designs = (try? c.decode([BackupDesign].self, forKey: .designs)) ?? []
        widgets = (try? c.decode([BackupWidget].self, forKey: .widgets)) ?? []
        reminders = (try? c.decode([BackupReminder].self, forKey: .reminders)) ?? []
        settings = (try? c.decode([String: String].self, forKey: .settings)) ?? [:]
    }
}

public struct BackupLocation: Codable {
    public var name: String; public var customLabel: String?; public var admin1: String = ""; public var countryCode: String = ""
    public var countryName: String = ""; public var latitude: Double; public var longitude: Double; public var timeZoneId: String
    public var source: String = "CITY"; public var geonameId: Int?; public var isFavorite = false; public var isDefault = false
    public var inCityList = true; public var sortOrder = 0
}

/// `spec` holds the design as a JSON string (Android format).
public struct BackupDesign: Codable { public var name: String; public var spec: String }
public struct BackupWidget: Codable { public var appWidgetId: Int; public var spec: String }
public struct BackupReminder: Codable { public var event: String; public var offsetMinutes: Int; public var enabled: Bool }

public enum BackupService {
    public static func make(locations: [SavedLocation], designs: [SavedDesign], reminders: [Reminder], settings: AppSettings) -> Data {
        var f = BackupFile()
        f.locations = locations.map {
            BackupLocation(name: $0.name, customLabel: $0.customLabel, admin1: $0.admin1, countryCode: $0.countryCode, countryName: $0.countryName,
                           latitude: $0.latitude, longitude: $0.longitude, timeZoneId: $0.timeZoneId, source: $0.source.rawValue,
                           geonameId: $0.geonameId, isFavorite: $0.isFavorite, isDefault: $0.isDefault, inCityList: $0.inCityList, sortOrder: $0.sortOrder)
        }
        f.designs = designs.compactMap { d in
            guard let data = try? JSONEncoder().encode(d.spec), let s = String(data: data, encoding: .utf8) else { return nil }
            return BackupDesign(name: d.spec.name, spec: s)
        }
        f.reminders = reminders.map { BackupReminder(event: $0.event.rawValue, offsetMinutes: $0.offsetMinutes, enabled: $0.enabled) }
        f.settings = ["theme": settings.themeMode.rawValue, "timeFormat": settings.timeFormat.rawValue,
                      "dateStyle": settings.dateStyle.rawValue, "onlineSearch": String(settings.onlineSearch)]
        let enc = JSONEncoder(); enc.outputFormatting = [.prettyPrinted, .sortedKeys]
        return (try? enc.encode(f)) ?? Data()
    }

    public struct Imported { public var locations: [SavedLocation]; public var designs: [SavedDesign]; public var reminders: [Reminder]; public var settings: [String: String] }

    public static func parse(_ data: Data) throws -> Imported {
        let f = try JSONDecoder().decode(BackupFile.self, from: data)
        guard f.format == "dayarc-backup" || f.format == "suncanvas-backup" else {
            throw NSError(domain: "DayArc", code: 1, userInfo: [NSLocalizedDescriptionKey: "This is not a DayArc backup file"])
        }
        let locs = f.locations.map { b -> SavedLocation in
            var l = SavedLocation(name: b.name, latitude: b.latitude, longitude: b.longitude, timeZoneId: b.timeZoneId,
                                  source: LocationSource(rawValue: b.source) == .device ? .manual : (LocationSource(rawValue: b.source) ?? .manual),
                                  admin1: b.admin1, countryCode: b.countryCode, countryName: b.countryName, geonameId: b.geonameId)
            l.customLabel = b.customLabel; l.isFavorite = b.isFavorite; l.inCityList = b.inCityList; l.sortOrder = b.sortOrder + 1000
            return l
        }
        let designs = (f.designs.map { $0.spec } + f.widgets.map { $0.spec }).compactMap { s -> SavedDesign? in
            guard var spec = try? JSONDecoder().decode(WidgetSpec.self, from: Data(s.utf8)) else { return nil }
            spec.locationId = nil; spec.extraLocationIds = []
            return SavedDesign(spec: spec)
        }
        let reminders = f.reminders.compactMap { r -> Reminder? in
            guard let e = ReminderEvent(rawValue: r.event) else { return nil }
            var x = Reminder(event: e, offsetMinutes: r.offsetMinutes); x.enabled = r.enabled; return x
        }
        return Imported(locations: locs, designs: designs, reminders: reminders, settings: f.settings)
    }
}
