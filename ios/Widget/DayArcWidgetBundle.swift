import WidgetKit
import SwiftUI
import AppIntents
import DayArcCore

@main
struct DayArcWidgetBundle: WidgetBundle {
    init() { FontRegistry.registerAll() }
    var body: some Widget {
        DayArcHomeWidget()
        DayArcLockWidget()
    }
}

// MARK: - Entities

struct DesignEntity: AppEntity {
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Design"
    static var defaultQuery = DesignQuery()
    let id: String          // "d:<saved id>" or "t:<template id>"
    let name: String
    let detail: String
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)", subtitle: "\(detail)") }

    var spec: WidgetSpec {
        if id.hasPrefix("d:"), let d = SharedStore.loadDesigns().first(where: { $0.id == String(id.dropFirst(2)) }) { return d.spec }
        if id.hasPrefix("t:"), let t = WidgetTemplates.byId(String(id.dropFirst(2))) { return t.spec }
        return WidgetTemplates.default.spec
    }

    static func all() -> [DesignEntity] {
        SharedStore.loadDesigns().map { DesignEntity(id: "d:\($0.id)", name: $0.spec.name, detail: "My design") }
            + WidgetTemplates.all.map { DesignEntity(id: "t:\($0.id)", name: $0.name, detail: "Template · \($0.sizeClass.label)") }
    }
}

struct DesignQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [DesignEntity] {
        let all = DesignEntity.all()
        return identifiers.compactMap { id in all.first { $0.id == id } ?? (id.hasPrefix("d:") ? DesignEntity(id: id, name: "Saved design", detail: "") : nil) }
    }
    func suggestedEntities() async throws -> [DesignEntity] { DesignEntity.all() }
    func defaultResult() async -> DesignEntity? { DesignEntity.all().first }
}

/// A city. Searched cities carry their own data in the id, so the widget works even when it can't read the app's saved list.
struct CityEntity: AppEntity {
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "City"
    static var defaultQuery = CityQuery()
    let id: String
    let title: String
    let subtitle: String
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(title)", subtitle: "\(subtitle)") }

    init(saved l: SavedLocation) {
        id = "s:\(l.id)"; title = l.displayName; subtitle = l.source == .device ? "Current location" : l.subtitle
    }
    init(city c: City) {
        let fields = [c.name, String(c.latitude), String(c.longitude), c.timeZoneId, c.countryCode, c.countryName, c.admin1]
        id = "c:" + fields.map { $0.replacingOccurrences(of: "|", with: " ") }.joined(separator: "|")
        title = c.name; subtitle = c.region
    }

    var location: SavedLocation? {
        if id.hasPrefix("s:") { return SharedStore.loadLocations().first { $0.id == String(id.dropFirst(2)) } }
        guard id.hasPrefix("c:") else { return nil }
        let f = id.dropFirst(2).split(separator: "|", omittingEmptySubsequences: false).map(String.init)
        guard f.count >= 4, let lat = Double(f[1]), let lon = Double(f[2]), TimeZone(identifier: f[3]) != nil else { return nil }
        var l = SavedLocation(name: f[0], latitude: lat, longitude: lon, timeZoneId: f[3],
                              admin1: f.count > 6 ? f[6] : "", countryCode: f.count > 4 ? f[4] : "", countryName: f.count > 5 ? f[5] : "")
        l.id = id
        return l
    }
}

struct CityQuery: EntityStringQuery {
    func entities(for identifiers: [String]) async throws -> [CityEntity] {
        let saved = SharedStore.loadLocations()
        return identifiers.compactMap { id in
            if id.hasPrefix("s:") { return saved.first { "s:\($0.id)" == id }.map(CityEntity.init(saved:)) }
            let probe = CityEntity(id: id)
            return probe.location.map { _ in probe }
        }
    }
    func entities(matching string: String) async throws -> [CityEntity] {
        let q = string.trimmingCharacters(in: .whitespaces)
        let saved = SharedStore.loadLocations().filter { q.isEmpty || $0.displayName.localizedCaseInsensitiveContains(q) }.map(CityEntity.init(saved:))
        guard q.count >= 2 else { return saved }
        return saved + CityDatabase.streamSearch(q, limit: 25).map(CityEntity.init(city:))
    }
    func suggestedEntities() async throws -> [CityEntity] {
        SharedStore.loadLocations().sorted { $0.sortOrder < $1.sortOrder }.map(CityEntity.init(saved:))
    }
}

extension CityEntity {
    /// Rebuild an entity from a stored id (title is cosmetic here).
    init(id: String) {
        self.id = id
        let f = id.dropFirst(2).split(separator: "|", omittingEmptySubsequences: false).map(String.init)
        title = f.first ?? "City"; subtitle = f.count > 5 ? f[5] : ""
    }
}

// MARK: - Intents

struct HomeWidgetIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "DayArc widget"
    static var description = IntentDescription("Choose a design from DayArc's Widget Studio and the city it shows.")
    @Parameter(title: "Design") var design: DesignEntity?
    @Parameter(title: "City", description: "Leave empty to use your default place.") var city: CityEntity?
}

enum LockStyle: String, AppEnum {
    case nextEvent, sunTimes, daylight
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Show"
    static var caseDisplayRepresentations: [LockStyle: DisplayRepresentation] = [
        .nextEvent: "Next sunrise or sunset", .sunTimes: "Sunrise & sunset", .daylight: "Daylight left",
    ]
}

struct LockWidgetIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "DayArc Lock Screen"
    static var description = IntentDescription("Sunrise and sunset on your Lock Screen.")
    @Parameter(title: "City", description: "Leave empty to use your default place.") var city: CityEntity?
    @Parameter(title: "Show", default: .nextEvent) var style: LockStyle
}

// MARK: - Shared helpers

enum WidgetData {
    /// The widget's city: chosen in Edit Widget → app default → a city matching the phone's time zone.
    static func location(_ entity: CityEntity?) -> SavedLocation? {
        if let l = entity?.location { return l }
        if let l = SharedStore.resolveLocation(nil) { return l }
        let zone = TimeZone.current.identifier
        let guess = zone.split(separator: "/").last.map { $0.replacingOccurrences(of: "_", with: " ") } ?? ""
        if let c = CityDatabase.streamSearch(guess, limit: 5).first(where: { $0.timeZoneId == zone }) { return c.toLocation() }
        return nil
    }

    /// Times when the face should be redrawn: every minute while a clock or minute countdown is visible,
    /// otherwise every 15 minutes, plus each sunrise/sunset/golden-hour boundary.
    static func dates(for spec: WidgetSpec?, location: SavedLocation?, from now: Date) -> [Date] {
        let start = Date(timeIntervalSince1970: (now.timeIntervalSince1970 / 60).rounded(.down) * 60)
        let minute: Bool = {
            guard let s = spec else { return false }
            if s.has(.time) || [.clock, .multiCity].contains(s.layout) { return true }
            let countdown = s.has(.countdownSunrise) || s.has(.countdownSunset) || s.has(.nextEvent) || s.layout == .nextEvent
            return countdown && !s.liveCountdown
        }()
        var out: [Date]
        if minute {
            out = (0..<60).map { start.addingTimeInterval(Double($0) * 60) }
        } else {
            out = (0..<24).map { start.addingTimeInterval(Double($0) * 900) }
            if let l = location {
                let end = out.last!.addingTimeInterval(900)
                let d = LocalDate(now, in: l.zone)
                for day in [d, d.adding(days: 1)] {
                    let s = SolarService.shared.day(l, day)
                    let marks = s.events.map(\.date) + [s.goldenMorning?.start, s.goldenMorning?.end, s.goldenEvening?.start, s.goldenEvening?.end].compactMap { $0 }
                    out += marks.filter { $0 > now && $0 < end }.map { $0.addingTimeInterval(1) }
                }
                out.sort()
            }
        }
        return out
    }
}

// MARK: - Home Screen widget

struct HomeEntry: TimelineEntry {
    let date: Date
    let spec: WidgetSpec
    let location: SavedLocation?
}

struct HomeProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> HomeEntry {
        HomeEntry(date: Date(), spec: WidgetTemplates.default.spec, location: WidgetData.location(nil))
    }
    func snapshot(for configuration: HomeWidgetIntent, in context: Context) async -> HomeEntry {
        HomeEntry(date: Date(), spec: configuration.design?.spec ?? WidgetTemplates.default.spec, location: WidgetData.location(configuration.city))
    }
    func timeline(for configuration: HomeWidgetIntent, in context: Context) async -> Timeline<HomeEntry> {
        let spec = configuration.design?.spec ?? WidgetTemplates.default.spec
        let loc = WidgetData.location(configuration.city)
        let entries = WidgetData.dates(for: spec, location: loc, from: Date()).map { HomeEntry(date: $0, spec: spec, location: loc) }
        return Timeline(entries: entries, policy: .atEnd)
    }
}

struct HomeEntryView: View {
    let entry: HomeEntry
    var body: some View {
        Group {
            if entry.location == nil {
                VStack(spacing: 6) {
                    GlyphView(.sunrise, color: Palette.amber).frame(width: 30, height: 30)
                    Text("Open DayArc to choose a place").font(.caption).multilineTextAlignment(.center).foregroundStyle(.white)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Palette.navy900)
            } else {
                WidgetFace(spec: entry.spec, data: FaceData.load(for: entry.spec, now: entry.date, isWidget: true, overrideLocation: entry.location))
            }
        }
        .containerBackground(for: .widget) { Color.clear }
    }
}

struct DayArcHomeWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "DayArcHome", intent: HomeWidgetIntent.self, provider: HomeProvider()) { entry in
            HomeEntryView(entry: entry)
        }
        .configurationDisplayName("DayArc")
        .description("Your own sunrise & sunset design. Touch and hold → Edit Widget to pick a design and city.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .systemExtraLarge])
        .contentMarginsDisabled()
    }
}

// MARK: - Lock Screen widget

struct LockEntry: TimelineEntry {
    let date: Date
    let location: SavedLocation?
    let style: LockStyle
}

struct LockProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> LockEntry { LockEntry(date: Date(), location: WidgetData.location(nil), style: .nextEvent) }
    func snapshot(for c: LockWidgetIntent, in context: Context) async -> LockEntry { LockEntry(date: Date(), location: WidgetData.location(c.city), style: c.style) }
    func timeline(for c: LockWidgetIntent, in context: Context) async -> Timeline<LockEntry> {
        let loc = WidgetData.location(c.city)
        let entries = WidgetData.dates(for: nil, location: loc, from: Date()).map { LockEntry(date: $0, location: loc, style: c.style) }
        return Timeline(entries: entries, policy: .atEnd)
    }
}

struct LockEntryView: View {
    @Environment(\.widgetFamily) var family
    let entry: LockEntry

    var body: some View {
        content.containerBackground(for: .widget) { Color.clear }
    }

    @ViewBuilder private var content: some View {
        if let l = entry.location {
            let snap = SolarService.shared.snapshot(l, at: entry.date)
            let settings = SharedStore.loadSettings()
            let fmt = TimeFormatter(use24h: settings.use24h)
            let rise = fmt.time(snap.today.sunrise, l.zone), set = fmt.time(snap.today.sunset, l.zone)
            switch family {
            case .accessoryCircular: circular(snap, fmt, l)
            case .accessoryInline:
                Text(entry.style == .daylight ? "☀︎ \(snap.remainingDaylight.map { Durations.hm($0) + " left" } ?? "Sun down")" : "↑\(rise)  ↓\(set)")
            default: rectangular(snap, fmt, l, rise, set)
            }
        } else {
            Text("Open DayArc").font(.caption)
        }
    }

    private func circular(_ snap: SunSnapshot, _ fmt: TimeFormatter, _ l: SavedLocation) -> some View {
        let next = snap.nextSunEvent
        let isRise = next?.type == .sunrise
        return ZStack {
            AccessoryWidgetBackground()
            Gauge(value: snap.daylightProgress ?? snap.nightProgress ?? 0) {
                Image(systemName: isRise ? "sunrise.fill" : "sunset.fill")
            } currentValueLabel: {
                VStack(spacing: 0) {
                    Image(systemName: isRise ? "sunrise.fill" : "sunset.fill").font(.system(size: 10))
                    Text(next.map { shortTime(fmt.time($0.date, l.zone)) } ?? "—").font(.system(size: 12, weight: .semibold)).minimumScaleFactor(0.6)
                }
            }
            .gaugeStyle(.accessoryCircularCapacity)
        }
        .widgetAccentable()
    }

    private func shortTime(_ s: String) -> String { s.replacingOccurrences(of: " AM", with: "").replacingOccurrences(of: " PM", with: "") }

    private func rectangular(_ snap: SunSnapshot, _ fmt: TimeFormatter, _ l: SavedLocation, _ rise: String, _ set: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(l.displayName).font(.caption2.weight(.semibold)).widgetAccentable().lineLimit(1)
            switch entry.style {
            case .sunTimes:
                HStack(spacing: 8) { Label(rise, systemImage: "sunrise.fill"); Label(set, systemImage: "sunset.fill") }
                    .font(.system(size: 14, weight: .semibold)).labelStyle(TightLabel())
                Text("Daylight \(Durations.hm(snap.today.dayLength))").font(.caption2)
            case .daylight:
                if let rem = snap.remainingDaylight, let p = snap.daylightProgress {
                    Text("\(Durations.hm(rem)) of daylight left").font(.system(size: 13, weight: .semibold))
                    ProgressView(value: p).tint(.white)
                } else {
                    Text("Sun is down").font(.system(size: 13, weight: .semibold))
                    Text("Sunrise \(fmt.time(snap.next.nextSunrise, l.zone))").font(.caption2)
                }
            case .nextEvent:
                if let n = snap.nextSunEvent {
                    Label("\(n.type.label) \(fmt.time(n.date, l.zone))", systemImage: n.type == .sunrise ? "sunrise.fill" : "sunset.fill")
                        .font(.system(size: 14, weight: .semibold)).labelStyle(TightLabel())
                    Text(n.date, style: .relative).font(.caption2).monospacedDigit()
                } else {
                    Text(snap.today.dayType == .polarDay ? "Midnight sun" : "Polar night").font(.system(size: 14, weight: .semibold))
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct TightLabel: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 3) { configuration.icon; configuration.title }
    }
}

struct DayArcLockWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "DayArcLock", intent: LockWidgetIntent.self, provider: LockProvider()) { entry in
            LockEntryView(entry: entry)
        }
        .configurationDisplayName("DayArc Lock Screen")
        .description("Next sunrise or sunset, sun times, or daylight left.")
        .supportedFamilies([.accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}
