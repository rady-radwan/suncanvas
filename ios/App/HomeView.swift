import SwiftUI
import DayArcCore

struct HomeView: View {
    @EnvironmentObject var model: AppModel
    @State private var searching = false
    @State private var coords = false

    var body: some View {
        NavigationStack {
            Group {
                if let loc = model.active {
                    TimelineView(.everyMinute) { ctx in
                        HomeContent(loc: loc, snap: SolarService.shared.snapshot(loc, at: ctx.date))
                    }
                } else {
                    ContentUnavailableView {
                        Label("No place yet", systemImage: "location.slash")
                    } description: {
                        Text("Use your location or search for a city to see sunrise and sunset.")
                    } actions: {
                        Button("Use my location") { model.refreshLocation() }.buttonStyle(.borderedProminent)
                        Button("Search for a city") { searching = true }
                    }
                }
            }
            .navigationTitle(model.active?.displayName ?? "DayArc")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Section("My cities") {
                            ForEach(model.cityList) { l in
                                Button { model.setActive(l.id) } label: {
                                    if l.id == model.active?.id { Label(l.headline, systemImage: "checkmark") } else { Text(l.headline) }
                                }
                            }
                        }
                        Button { model.refreshLocation() } label: { Label("Use current location", systemImage: "location") }
                        Button { searching = true } label: { Label("Search city", systemImage: "magnifyingglass") }
                        Button { coords = true } label: { Label("Enter coordinates", systemImage: "scope") }
                    } label: {
                        if model.locating { ProgressView() } else { Image(systemName: "mappin.and.ellipse") }
                    }
                    .accessibilityLabel("Change place")
                }
            }
            .sheet(isPresented: $searching) {
                CitySearchView { loc in _ = model.add(loc, makeActive: true); searching = false }.environmentObject(model)
            }
            .sheet(isPresented: $coords) {
                CoordinatesView { loc in _ = model.add(loc, makeActive: true); coords = false }
            }
        }
    }
}

struct HomeContent: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.colorScheme) var scheme
    let loc: SavedLocation
    let snap: SunSnapshot

    private var fmt: TimeFormatter { model.formatter }
    private var zone: TimeZone { loc.zone }
    private var today: SolarDay { snap.today }

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                header
                arcCard
                nextEventCard
                tiles
                goldenBlue
                twilight
                positionCard
                tomorrowCard
                seasons
                Text("Times are calculated on-device with the NOAA solar algorithm and include standard atmospheric refraction.")
                    .font(.caption2).foregroundStyle(.secondary).multilineTextAlignment(.center).padding(.top, 8)
            }
            .padding(.horizontal, DS.screen).padding(.bottom, 24)
        }
        .background(DS.bg(scheme).ignoresSafeArea())
        .refreshable { if loc.source == .device { model.refreshLocation() } }
    }

    // MARK: sections

    private var header: some View {
        VStack(spacing: 2) {
            Text(loc.subtitle.isEmpty ? formatCoordinate(loc.latitude, latitude: true) + "  " + formatCoordinate(loc.longitude, latitude: false) : loc.subtitle)
                .font(.subheadline).foregroundStyle(.secondary)
            Ticking { now in
                let parts = fmt.clockParts(now, zone)
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(parts.0).font(DS.display(64, .light)).monospacedDigit()
                    if model.settings.showSeconds {
                        Text(secs(now)).font(DS.display(24, .light)).monospacedDigit().foregroundStyle(.secondary)
                    }
                    if let ap = parts.1 { Text(ap).font(DS.display(20)).foregroundStyle(.secondary) }
                }
                .accessibilityElement(children: .combine)
            }
            Text("\(fmt.date(snap.now, zone)) · \(TimeFormatter.abbreviation(zone, at: snap.now)) (\(TimeFormatter.utcOffset(zone, at: snap.now)))")
                .font(.footnote).foregroundStyle(.secondary)
            if loc.source == .device {
                Label("Current location", systemImage: "location.fill").font(.caption2).foregroundStyle(DS.blue(scheme)).padding(.top, 2)
            }
        }
        .frame(maxWidth: .infinity).padding(.top, 8)
    }

    private func secs(_ d: Date) -> String {
        var c = Calendar(identifier: .gregorian); c.timeZone = zone
        return String(format: ":%02d", c.component(.second, from: d))
    }

    private var arcCard: some View {
        Card {
            VStack(spacing: 10) {
                SunArc(progress: snap.daylightProgress, nightProgress: snap.nightProgress).frame(height: 130)
                HStack {
                    eventColumn("Sunrise", today.sunrise, .sunrise, DS.sunrise(scheme), alignment: .leading)
                    Spacer()
                    VStack(spacing: 2) {
                        Text("Solar noon").font(.caption).foregroundStyle(.secondary)
                        Text(fmt.time(today.solarNoon, zone)).font(DS.display(17))
                    }
                    Spacer()
                    eventColumn("Sunset", today.sunset, .sunset, DS.sunset(scheme), alignment: .trailing)
                }
                statusLine
            }
        }
    }

    private func eventColumn(_ title: String, _ date: Date?, _ g: Glyph, _ tint: Color, alignment: HorizontalAlignment) -> some View {
        VStack(alignment: alignment, spacing: 2) {
            HStack(spacing: 4) { GlyphView(g, color: tint).frame(width: 16, height: 16); Text(title).font(.caption).foregroundStyle(.secondary) }
            Text(date.map { fmt.time($0, zone) } ?? polarText).font(DS.display(24, .bold))
        }
        .accessibilityElement(children: .combine)
    }

    private var polarText: String { today.dayType == .polarDay ? "Sun up" : today.dayType == .polarNight ? "Sun down" : "—" }

    @ViewBuilder private var statusLine: some View {
        switch today.dayType {
        case .polarDay: Text("Midnight sun — the Sun stays above the horizon all day").font(.footnote).foregroundStyle(.secondary)
        case .polarNight: Text("Polar night — the Sun stays below the horizon all day").font(.footnote).foregroundStyle(.secondary)
        case .normal:
            if let rem = snap.remainingDaylight {
                Text("\(Durations.hm(rem)) of daylight left").font(.footnote.weight(.medium)).foregroundStyle(DS.sunrise(scheme))
            } else {
                Text("The Sun is down").font(.footnote).foregroundStyle(.secondary)
            }
        }
    }

    private var nextEventCard: some View {
        Card {
            HStack(spacing: 14) {
                let next = snap.nextSunEvent
                let isRise = next?.type == .sunrise
                GlyphView(isRise ? .sunrise : .sunset, color: isRise ? DS.sunrise(scheme) : DS.sunset(scheme)).frame(width: 34, height: 34)
                VStack(alignment: .leading, spacing: 2) {
                    Text(next.map { "Next \($0.type.label.lowercased())" } ?? "No sunrise or sunset soon").font(.caption).foregroundStyle(.secondary)
                    if let n = next {
                        Text(timerInterval: snap.now...max(n.date, snap.now.addingTimeInterval(1)), countsDown: true)
                            .font(DS.display(30)).monospacedDigit()
                        Text(fmt.timeWithDay(n.date, zone, reference: snap.localDate)).font(.footnote).foregroundStyle(.secondary)
                    }
                }
                Spacer()
            }
        }
    }

    private var tiles: some View {
        let change = snap.daylightChange
        let changeText = abs(change) < 1 ? "Same as yesterday" : Durations.signedMinSec(change) + " vs yesterday"
        return LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
            StatTile(label: "Day length", value: Durations.hm(today.daylight), icon: "sun.max", tint: DS.sunrise(scheme), supporting: changeText)
            StatTile(label: "Night length", value: Durations.hm(max(0, today.dayLength - today.daylight)), icon: "moon.stars", tint: DS.blue(scheme))
            StatTile(label: "Noon elevation", value: String(format: "%.1f°", today.noonElevation), icon: "angle", tint: Palette.golden)
            StatTile(label: "Solar midnight", value: fmt.time(today.solarMidnight, zone), icon: "moon", tint: DS.blue(scheme))
        }
    }

    private var goldenBlue: some View {
        VStack(spacing: 0) {
            SectionTitle(title: "Golden & blue hour")
            Card {
                VStack(spacing: 0) {
                    LabeledRow(label: "Morning blue hour", value: window(today.blueMorning), valueColor: DS.blue(scheme))
                    LabeledRow(label: "Morning golden hour", value: window(today.goldenMorning), valueColor: DS.sunrise(scheme))
                    Divider().padding(.vertical, 4)
                    LabeledRow(label: "Evening golden hour", value: window(today.goldenEvening), valueColor: DS.sunrise(scheme))
                    LabeledRow(label: "Evening blue hour", value: window(today.blueEvening), valueColor: DS.blue(scheme))
                    if snap.inGoldenHour {
                        Label("It's golden hour now", systemImage: "camera.aperture").font(.footnote.weight(.semibold)).foregroundStyle(Palette.golden).padding(.top, 6)
                    }
                }
            }
        }
    }

    private func window(_ w: TimeWindow?) -> String {
        guard let w = w else { return "None today" }
        return "\(fmt.time(w.start, zone)) – \(fmt.time(w.end, zone))"
    }

    private var twilight: some View {
        VStack(spacing: 0) {
            SectionTitle(title: "Twilight")
            Card {
                VStack(spacing: 0) {
                    twRow("Astronomical", today.astronomicalDawn, today.astronomicalDusk)
                    twRow("Nautical", today.nauticalDawn, today.nauticalDusk)
                    twRow("Civil", today.civilDawn, today.civilDusk)
                    Text("Dawn begins when the Sun climbs to 18°, 12° and 6° below the horizon; dusk mirrors it in the evening.")
                        .font(.caption2).foregroundStyle(.secondary).padding(.top, 6)
                }
            }
        }
    }

    private func twRow(_ name: String, _ dawn: Date?, _ dusk: Date?) -> some View {
        HStack {
            Text(name).foregroundStyle(.secondary)
            Spacer()
            Text("\(fmt.time(dawn, zone)) · \(fmt.time(dusk, zone))").fontWeight(.medium).monospacedDigit()
        }
        .font(.subheadline).padding(.vertical, 5)
    }

    private var positionCard: some View {
        VStack(spacing: 0) {
            SectionTitle(title: "Sun position now")
            Card {
                VStack(spacing: 10) {
                    SunPositionDial(azimuth: snap.position.azimuth, elevation: snap.position.elevation).frame(maxWidth: .infinity)
                    HStack {
                        StatMini("Azimuth", String(format: "%.0f° %@", snap.position.azimuth, compassPoint(snap.position.azimuth)))
                        StatMini("Elevation", String(format: "%.1f°", snap.position.elevation))
                        StatMini("Declination", String(format: "%.1f°", snap.position.declination))
                    }
                    Text(String(format: "%@  %@ · Equation of time %+.1f min",
                                formatCoordinate(loc.latitude, latitude: true), formatCoordinate(loc.longitude, latitude: false), snap.position.equationOfTimeMinutes))
                        .font(.caption2).foregroundStyle(.secondary)
                }
            }
        }
    }

    private var tomorrowCard: some View {
        let t = snap.tomorrow
        return VStack(spacing: 0) {
            SectionTitle(title: "Tomorrow")
            Card {
                HStack {
                    StatMini("Sunrise", fmt.time(t.sunrise, zone))
                    StatMini("Sunset", fmt.time(t.sunset, zone))
                    StatMini("Day length", Durations.hm(t.daylight))
                }
            }
        }
    }

    private var seasons: some View {
        VStack(spacing: 0) {
            SectionTitle(title: "Seasons")
            Card {
                VStack(spacing: 0) {
                    ForEach(Seasons.upcoming(from: snap.now), id: \.date) { e in
                        LabeledRow(label: e.type.label, value: "\(fmt.date(e.date, zone)) · \(fmt.time(e.date, zone))")
                    }
                }
            }
        }
    }
}

struct StatMini: View {
    let label: String, value: String
    init(_ label: String, _ value: String) { self.label = label; self.value = value }
    var body: some View {
        VStack(spacing: 2) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            Text(value).font(DS.display(16)).lineLimit(1).minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }
}
