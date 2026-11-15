import SwiftUI
import DayArcCore

struct CitiesView: View {
    @EnvironmentObject var model: AppModel
    @State private var mode = ProcessInfo.processInfo.arguments.contains("-world") ? 1 : 0
    @State private var adding = false
    @State private var coords = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Picker("View", selection: $mode) {
                    Text("My cities").tag(0)
                    Text("World clock").tag(1)
                }
                .pickerStyle(.segmented).padding(.horizontal, DS.screen).padding(.vertical, 8)
                if mode == 0 { MyCitiesList() } else { WorldClockView() }
            }
            .navigationTitle(mode == 0 ? "My cities" : "World clock")
            .navigationDestination(for: String.self) { LocationDetailView(id: $0) }
            .toolbar {
                if mode == 0 { ToolbarItem(placement: .topBarLeading) { EditButton() } }
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button { adding = true } label: { Label("Search city", systemImage: "magnifyingglass") }
                        Button { coords = true } label: { Label("Enter coordinates", systemImage: "scope") }
                        Button { model.refreshLocation() } label: { Label("Current location", systemImage: "location") }
                    } label: { Image(systemName: "plus") }
                    .accessibilityLabel("Add a place")
                }
            }
            .sheet(isPresented: $adding) {
                CitySearchView { l in _ = model.add(l); adding = false; model.show("Added \(l.headline)") }.environmentObject(model)
            }
            .sheet(isPresented: $coords) {
                CoordinatesView { l in _ = model.add(l); coords = false }
            }
        }
    }
}

struct MyCitiesList: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        TimelineView(.everyMinute) { ctx in
            List {
                if model.cityList.isEmpty {
                    Text("Add cities to compare sunrise, sunset and local time at a glance.").foregroundStyle(.secondary)
                }
                ForEach(model.cityList) { l in
                    NavigationLink(value: l.id) { CityRow(loc: l, now: ctx.date, active: l.id == model.active?.id) }
                        .swipeActions(edge: .trailing) {
                            if l.source != .device {
                                Button(role: .destructive) { model.delete(l.id) } label: { Label("Remove", systemImage: "trash") }
                            }
                        }
                        .swipeActions(edge: .leading) {
                            Button { model.setFavorite(l.id, !l.isFavorite) } label: { Label("Favorite", systemImage: l.isFavorite ? "heart.slash" : "heart") }.tint(.pink)
                            Button { model.setActive(l.id) } label: { Label("Show", systemImage: "sun.horizon") }.tint(Palette.amberDeep)
                        }
                        .contextMenu {
                            Button { model.setActive(l.id) } label: { Label("Show on Today", systemImage: "sun.horizon") }
                            Button { model.setDefault(l.id) } label: { Label("Make default", systemImage: "star.circle") }
                            Button { model.setFavorite(l.id, !l.isFavorite) } label: { Label(l.isFavorite ? "Unfavorite" : "Favorite", systemImage: "heart") }
                        }
                }
                .onMove { model.move(from: $0, to: $1) }
            }
            .listStyle(.insetGrouped)
        }
    }
}

struct CityRow: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.colorScheme) var scheme
    let loc: SavedLocation
    let now: Date
    let active: Bool

    var body: some View {
        let day = SolarService.shared.day(loc, LocalDate(now, in: loc.zone))
        let isDay = SolarService.shared.position(loc, at: now).geometricElevation > SolarAltitude.horizon
        let fmt = model.formatter
        HStack(spacing: 12) {
            GlyphView(isDay ? .sun : .moon, color: isDay ? DS.sunrise(scheme) : DS.blue(scheme)).frame(width: 22, height: 22)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Text(loc.displayName).font(.body.weight(.semibold)).lineLimit(1)
                    if loc.source == .device { Image(systemName: "location.fill").font(.caption2).foregroundStyle(DS.blue(scheme)) }
                    if loc.isFavorite { Image(systemName: "heart.fill").font(.caption2).foregroundStyle(.pink) }
                    if loc.isDefault { Image(systemName: "star.fill").font(.caption2).foregroundStyle(Palette.golden) }
                }
                Text("↑ \(fmt.time(day.sunrise, loc.zone))   ↓ \(fmt.time(day.sunset, loc.zone))   \(Durations.hm(day.daylight))")
                    .font(.caption).foregroundStyle(.secondary).monospacedDigit()
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(fmt.time(now, loc.zone)).font(DS.display(20)).monospacedDigit()
                Text(TimeFormatter.utcOffset(loc.zone, at: now)).font(.caption2).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
        .overlay(alignment: .leading) { if active { Capsule().fill(Palette.amber).frame(width: 3).offset(x: -12) } }
        .accessibilityElement(children: .combine)
    }
}

struct WorldClockView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.colorScheme) var scheme
    @State private var offsetHours: Double = 0
    @State private var editing = false

    private var visible: [SavedLocation] { model.cityList.filter { !model.settings.worldClockHidden.contains($0.id) } }

    var body: some View {
        Ticking { tick in
            let now = tick.addingTimeInterval(offsetHours * 3600)
            ScrollView {
                VStack(spacing: 12) {
                    Card {
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text(offsetHours == 0 ? "Now" : String(format: "%@%.1f h from now", offsetHours > 0 ? "+" : "−", abs(offsetHours)))
                                    .font(.subheadline.weight(.semibold))
                                Spacer()
                                if offsetHours != 0 { Button("Reset") { offsetHours = 0 }.font(.subheadline) }
                            }
                            Slider(value: $offsetHours, in: -24...24, step: 0.5)
                                .accessibilityLabel("Time travel")
                            Text("Drag to see what time it will be — and whether the Sun is up — in every city.").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    Picker("Style", selection: Binding(get: { model.settings.worldClockStyle }, set: { model.settings.worldClockStyle = $0; model.saveSettings() })) {
                        Text("Digital").tag(ClockStyle.digital)
                        Text("Analog").tag(ClockStyle.analog)
                    }
                    .pickerStyle(.segmented)

                    if visible.isEmpty {
                        Text("No cities to show. Add cities in My cities.").foregroundStyle(.secondary).padding(.top, 30)
                    }
                    if model.settings.worldClockStyle == .analog {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 12)], spacing: 12) {
                            ForEach(visible) { l in analogCell(l, now) }
                        }
                    } else {
                        ForEach(visible) { l in digitalRow(l, now) }
                    }
                    Button(editing ? "Done" : "Choose cities") { editing.toggle() }.padding(.top, 4)
                    if editing {
                        Card {
                            VStack(alignment: .leading) {
                                ForEach(model.cityList) { l in
                                    Toggle(l.displayName, isOn: Binding(
                                        get: { !model.settings.worldClockHidden.contains(l.id) },
                                        set: { on in
                                            if on { model.settings.worldClockHidden.removeAll { $0 == l.id } } else { model.settings.worldClockHidden.append(l.id) }
                                            model.saveSettings()
                                        }))
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, DS.screen).padding(.bottom, 24)
            }
        }
    }

    private func isDay(_ l: SavedLocation, _ d: Date) -> Bool { SolarService.shared.position(l, at: d).geometricElevation > SolarAltitude.horizon }

    private func relative(_ l: SavedLocation, _ d: Date) -> String {
        let local = model.active?.zone ?? .current
        let diff = (l.zone.secondsFromGMT(for: d) - local.secondsFromGMT(for: d)) / 60
        if diff == 0 { return "Same time" }
        let h = abs(diff) / 60, m = abs(diff) % 60
        return (diff > 0 ? "+" : "−") + (m == 0 ? "\(h)h" : "\(h)h \(m)m")
    }

    private func dayLabel(_ l: SavedLocation, _ d: Date) -> String {
        let local = model.active?.zone ?? .current
        let a = LocalDate(d, in: l.zone), b = LocalDate(d, in: local)
        return a == b ? "Today" : (a > b ? "Tomorrow" : "Yesterday")
    }

    private func digitalRow(_ l: SavedLocation, _ d: Date) -> some View {
        let day = isDay(l, d)
        return HStack(spacing: 12) {
            GlyphView(day ? .sun : .moon, color: day ? DS.sunrise(scheme) : DS.blue(scheme)).frame(width: 26, height: 26)
            VStack(alignment: .leading, spacing: 2) {
                Text(l.displayName).font(.headline)
                Text("\(dayLabel(l, d)), \(relative(l, d)) · \(TimeFormatter.abbreviation(l.zone, at: d))").font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Text(model.formatter.time(d, l.zone)).font(DS.display(30, .light)).monospacedDigit()
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: DS.corner, style: .continuous)
            .fill(day ? AnyShapeStyle(LinearGradient(colors: [DS.sunrise(scheme).opacity(0.18), DS.card(scheme)], startPoint: .leading, endPoint: .trailing))
                      : AnyShapeStyle(LinearGradient(colors: [Palette.navy800.opacity(scheme == .dark ? 1 : 0.15), DS.card(scheme)], startPoint: .leading, endPoint: .trailing))))
        .accessibilityElement(children: .combine)
    }

    private func analogCell(_ l: SavedLocation, _ d: Date) -> some View {
        Card(padding: 12) {
            VStack(spacing: 6) {
                AnalogClock(date: d, zone: l.zone, isDay: isDay(l, d))
                Text(l.displayName).font(.subheadline.weight(.semibold)).lineLimit(1)
                Text("\(model.formatter.time(d, l.zone)) · \(relative(l, d))").font(.caption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
        }
    }
}
