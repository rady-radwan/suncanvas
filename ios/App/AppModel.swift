import SwiftUI
import WidgetKit
import CoreLocation
import UserNotifications
import DayArcCore

/// App-wide state: saved places, designs, reminders and settings, persisted as JSON in the shared
/// App Group container so widgets can read them.
@MainActor
final class AppModel: ObservableObject {
    @Published var locations: [SavedLocation] = SharedStore.loadLocations().sorted { $0.sortOrder < $1.sortOrder }
    @Published var designs: [SavedDesign] = SharedStore.loadDesigns()
    @Published var reminders: [Reminder] = SharedStore.loadReminders()
    @Published var settings: AppSettings = SharedStore.loadSettings()
    @Published var locating = false
    @Published var toast: String? = nil

    let location = LocationService()

    var active: SavedLocation? { SharedStore.resolveLocation(settings.activeLocationId, in: locations) }
    var formatter: TimeFormatter { settings.formatter }
    var cityList: [SavedLocation] { locations.filter { $0.inCityList } }

    // MARK: persistence

    private func saveLocations() { SharedStore.saveLocations(locations); widgetsChanged(); rescheduleReminders() }
    func saveSettings() { SharedStore.saveSettings(settings); widgetsChanged() }
    private func saveDesigns() { SharedStore.saveDesigns(designs); widgetsChanged() }
    private func saveReminders() { SharedStore.saveReminders(reminders); rescheduleReminders() }

    func widgetsChanged() { WidgetCenter.shared.reloadAllTimelines() }

    func show(_ message: String) {
        toast = message
        Task { try? await Task.sleep(nanoseconds: 2_600_000_000); if toast == message { toast = nil } }
    }

    // MARK: locations

    func setActive(_ id: String) { settings.activeLocationId = id; saveSettings() }

    func setDefault(_ id: String) {
        for i in locations.indices { locations[i].isDefault = locations[i].id == id }
        saveLocations()
    }

    func setFavorite(_ id: String, _ fav: Bool) {
        if let i = locations.firstIndex(where: { $0.id == id }) { locations[i].isFavorite = fav; saveLocations() }
    }

    func rename(_ id: String, _ label: String?) {
        if let i = locations.firstIndex(where: { $0.id == id }) {
            let t = label?.trimmingCharacters(in: .whitespaces)
            locations[i].customLabel = (t?.isEmpty ?? true) ? nil : t
            saveLocations()
        }
    }

    func delete(_ id: String) {
        let wasDefault = locations.first { $0.id == id }?.isDefault == true
        locations.removeAll { $0.id == id }
        if wasDefault, !locations.isEmpty { locations[0].isDefault = true }
        if settings.activeLocationId == id { settings.activeLocationId = nil; saveSettings() }
        saveLocations()
    }

    func move(from: IndexSet, to: Int) {
        var list = cityList
        list.move(fromOffsets: from, toOffset: to)
        for (i, l) in list.enumerated() { if let j = locations.firstIndex(where: { $0.id == l.id }) { locations[j].sortOrder = i + 1 } }
        locations.sort { $0.sortOrder < $1.sortOrder }
        saveLocations()
    }

    @discardableResult
    func add(_ loc: SavedLocation, makeActive: Bool = false, makeDefault: Bool = false) -> String {
        var id: String
        if let existing = locations.first(where: { $0.source != .device && abs($0.latitude - loc.latitude) < 0.01 && abs($0.longitude - loc.longitude) < 0.01 }) {
            id = existing.id
        } else {
            var l = loc
            l.sortOrder = (locations.map { $0.sortOrder }.max() ?? 0) + 1
            locations.append(l)
            id = l.id
        }
        if makeDefault || !locations.contains(where: { $0.isDefault }) {
            for i in locations.indices { locations[i].isDefault = locations[i].id == id }
        }
        saveLocations()
        if makeActive { setActive(id) }
        return id
    }

    /// One-shot foreground location → stored in the single DEVICE row.
    func refreshLocation(completion: ((Bool) -> Void)? = nil) {
        guard !locating else { return }
        locating = true
        location.requestOnce { [weak self] result in
            Task { @MainActor in self?.handleLocation(result, completion: completion) }
        }
    }

    private func handleLocation(_ result: Result<CLLocationCoordinate2D, Error>, completion: ((Bool) -> Void)?) {
            self.locating = false
            switch result {
            case .success(let coord):
                let place = TimeZoneResolver.resolve(latitude: coord.latitude, longitude: coord.longitude)
                var row = self.locations.first { $0.source == .device }
                    ?? SavedLocation(name: place.name, latitude: coord.latitude, longitude: coord.longitude, timeZoneId: place.timeZoneId, source: .device)
                row.name = place.name; row.admin1 = place.admin1; row.countryCode = place.countryCode; row.countryName = place.countryName
                row.latitude = coord.latitude; row.longitude = coord.longitude; row.timeZoneId = place.timeZoneId
                row.geonameId = place.nearestCity?.geonameId
                if let i = self.locations.firstIndex(where: { $0.id == row.id }) { self.locations[i] = row }
                else {
                    if !self.locations.contains(where: { $0.isDefault }) { row.isDefault = true }
                    self.locations.insert(row, at: 0)
                }
                self.saveLocations()
                self.setActive(row.id)
                self.show("Location updated: \(row.headline)")
                completion?(true)
            case .failure(let err):
                self.show(err.localizedDescription)
                completion?(false)
            }
    }

    // MARK: designs

    @discardableResult
    func saveDesign(_ spec: WidgetSpec, id: String?) -> String {
        if let id = id, let i = designs.firstIndex(where: { $0.id == id }) {
            designs[i].spec = spec; designs[i].updatedAt = Date(); saveDesigns(); return id
        }
        let d = SavedDesign(spec: spec)
        designs.insert(d, at: 0); saveDesigns()
        return d.id
    }

    func duplicateDesign(_ id: String) {
        guard let d = designs.first(where: { $0.id == id }) else { return }
        var s = d.spec; s.name += " copy"
        designs.insert(SavedDesign(spec: s), at: 0); saveDesigns()
    }

    func deleteDesign(_ id: String) { designs.removeAll { $0.id == id }; saveDesigns() }

    // MARK: reminders

    func addReminder(_ r: Reminder) { reminders.append(r); saveReminders() }
    func updateReminder(_ r: Reminder) { if let i = reminders.firstIndex(where: { $0.id == r.id }) { reminders[i] = r; saveReminders() } }
    func deleteReminder(_ id: String) { reminders.removeAll { $0.id == id }; saveReminders() }

    func setNotifications(_ on: Bool) {
        if on {
            UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { granted, _ in
                Task { @MainActor in
                    self.settings.notificationsEnabled = granted
                    if granted && self.reminders.isEmpty {
                        self.reminders = [Reminder(event: .sunset, offsetMinutes: -30), Reminder(event: .sunrise, offsetMinutes: -15)]
                        SharedStore.saveReminders(self.reminders)
                    }
                    if !granted { self.show("Notifications are off — allow them in iPhone Settings") }
                    self.saveSettings(); self.rescheduleReminders()
                }
            }
        } else {
            settings.notificationsEnabled = false; saveSettings(); rescheduleReminders()
        }
    }

    func rescheduleReminders() {
        Notifications.reschedule(reminders: reminders, enabled: settings.notificationsEnabled, location: SharedStore.resolveLocation(nil, in: locations), formatter: formatter)
    }

    // MARK: backup

    func exportBackup() -> Data { BackupService.make(locations: locations, designs: designs, reminders: reminders, settings: settings) }

    func importBackup(_ data: Data) throws -> String {
        let imp = try BackupService.parse(data)
        var added = 0
        for l in imp.locations where !locations.contains(where: { abs($0.latitude - l.latitude) < 0.001 && abs($0.longitude - l.longitude) < 0.001 }) {
            locations.append(l); added += 1
        }
        if !locations.contains(where: { $0.isDefault }), !locations.isEmpty { locations[0].isDefault = true }
        designs.insert(contentsOf: imp.designs, at: 0)
        reminders += imp.reminders
        if let t = imp.settings["theme"], let m = ThemeMode(rawValue: t) { settings.themeMode = m }
        if let t = imp.settings["timeFormat"], let m = TimeFormatPref(rawValue: t) { settings.timeFormat = m }
        if let t = imp.settings["dateStyle"], let m = DateStyle(rawValue: t) { settings.dateStyle = m }
        saveLocations(); saveDesigns(); saveReminders(); saveSettings()
        return "Imported \(added) places, \(imp.designs.count) widget designs, \(imp.reminders.count) reminders"
    }
}

// MARK: - Location

final class LocationService: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var completion: ((Result<CLLocationCoordinate2D, Error>) -> Void)?

    struct Failure: LocalizedError { let errorDescription: String? }

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
    }

    var authorized: Bool { [.authorizedWhenInUse, .authorizedAlways].contains(manager.authorizationStatus) }

    func requestOnce(_ completion: @escaping (Result<CLLocationCoordinate2D, Error>) -> Void) {
        self.completion = completion
        switch manager.authorizationStatus {
        case .notDetermined: manager.requestWhenInUseAuthorization()
        case .denied, .restricted: finish(.failure(Failure(errorDescription: "Location is off for DayArc — search for a city instead, or allow location in iPhone Settings")))
        default:
            // A recent cached fix costs no radio time.
            if let l = manager.location, Date().timeIntervalSince(l.timestamp) < 600 { finish(.success(l.coordinate)) }
            else { manager.requestLocation() }
        }
    }

    private func finish(_ r: Result<CLLocationCoordinate2D, Error>) {
        DispatchQueue.main.async { self.completion?(r); self.completion = nil }
    }

    func locationManagerDidChangeAuthorization(_ m: CLLocationManager) {
        guard completion != nil else { return }
        switch m.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways: m.requestLocation()
        case .denied, .restricted: finish(.failure(Failure(errorDescription: "Location permission denied — showing your saved location")))
        default: break
        }
    }

    func locationManager(_ m: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if let l = locations.last { finish(.success(l.coordinate)) }
    }

    func locationManager(_ m: CLLocationManager, didFailWithError error: Error) {
        finish(.failure(Failure(errorDescription: "Couldn't get a location right now — showing your saved location")))
    }
}

// MARK: - Notifications

enum Notifications {
    /// Schedules the next 7 days of each enabled reminder (iOS allows 64 pending), in the location's own zone.
    static func reschedule(reminders: [Reminder], enabled: Bool, location: SavedLocation?, formatter: TimeFormatter) {
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()
        guard enabled, let loc = location else { return }
        let now = Date()
        var requests: [(Date, UNNotificationRequest)] = []
        let today = LocalDate(now, in: loc.zone)
        for r in reminders where r.enabled {
            for dayOffset in 0..<8 {
                let day = SolarService.shared.day(loc, today.adding(days: dayOffset))
                guard let event = r.event.instant(in: day) else { continue }
                let fire = event.addingTimeInterval(TimeInterval(r.offsetMinutes * 60))
                guard fire > now.addingTimeInterval(20) else { continue }
                let content = UNMutableNotificationContent()
                content.title = r.offsetMinutes == 0 ? "\(r.event.label) now" : r.offsetMinutes < 0 ? "\(r.event.label) in \(-r.offsetMinutes) min" : "\(r.event.label) was \(r.offsetMinutes) min ago"
                content.body = "\(loc.displayName) · \(r.event.label) at \(formatter.time(event, loc.zone))"
                content.sound = .default
                let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: fire)
                let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
                requests.append((fire, UNNotificationRequest(identifier: "\(r.id)-\(dayOffset)", content: content, trigger: trigger)))
            }
        }
        for item in requests.sorted(by: { $0.0 < $1.0 }).prefix(60) {
            center.add(item.1)
        }
    }
}
