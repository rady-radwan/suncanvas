import Foundation

/// Small JSON files shared between the app and its widget through an App Group container.
///
/// Sideloading tools (Sideloadly, AltStore) may rename the App Group when they re-sign the app with a
/// personal Apple ID, so the group identifier is read from the provisioning profile embedded in the
/// bundle when present. If no group is available, data stays in the app's own container and widgets
/// fall back to built-in designs and cities chosen in the widget's settings.
public enum SharedStore {
    public static let defaultGroupId = "group.com.dayarc.app"

    public static let groupId: String = {
        if let id = groupFromProvisioningProfile() { return id }
        return defaultGroupId
    }()

    public static var containerURL: URL {
        if let url = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupId) { return url }
        return FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
    }

    public static var isShared: Bool {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupId) != nil
    }

    static func groupFromProvisioningProfile() -> String? {
        // The widget extension lives in Bundle.main of the .appex; both carry their own embedded profile.
        guard let url = Bundle.main.url(forResource: "embedded", withExtension: "mobileprovision"),
              let data = try? Data(contentsOf: url),
              let text = String(data: data, encoding: .isoLatin1),
              let start = text.range(of: "<?xml"), let end = text.range(of: "</plist>") else { return nil }
        let plistText = String(text[start.lowerBound..<end.upperBound])
        guard let plist = try? PropertyListSerialization.propertyList(from: Data(plistText.utf8), format: nil) as? [String: Any],
              let ent = plist["Entitlements"] as? [String: Any],
              let groups = ent["com.apple.security.application-groups"] as? [String] else { return nil }
        return groups.first
    }

    static let dir: URL = {
        let d = containerURL.appendingPathComponent("DayArc", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }()

    public static var imagesDir: URL {
        let d = dir.appendingPathComponent("images", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }

    static func read<T: Decodable>(_ name: String, _ type: T.Type) -> T? {
        guard let data = try? Data(contentsOf: dir.appendingPathComponent(name)) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    static func write<T: Encodable>(_ name: String, _ value: T) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        try? data.write(to: dir.appendingPathComponent(name), options: .atomic)
    }

    public static func loadLocations() -> [SavedLocation] { read("locations.json", [SavedLocation].self) ?? [] }
    public static func saveLocations(_ v: [SavedLocation]) { write("locations.json", v) }
    public static func loadDesigns() -> [SavedDesign] { read("designs.json", [SavedDesign].self) ?? [] }
    public static func saveDesigns(_ v: [SavedDesign]) { write("designs.json", v) }
    public static func loadSettings() -> AppSettings { read("settings.json", AppSettings.self) ?? AppSettings() }
    public static func saveSettings(_ v: AppSettings) { write("settings.json", v) }
    public static func loadReminders() -> [Reminder] { read("reminders.json", [Reminder].self) ?? [] }
    public static func saveReminders(_ v: [Reminder]) { write("reminders.json", v) }

    /// Location to show: explicit id → default → first saved.
    public static func resolveLocation(_ id: String?, in list: [SavedLocation]? = nil) -> SavedLocation? {
        let all = list ?? loadLocations()
        if let id = id, let l = all.first(where: { $0.id == id }) { return l }
        return all.first(where: { $0.isDefault }) ?? all.first
    }
}
