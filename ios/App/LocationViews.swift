import SwiftUI
import DayArcCore

/// Optional online search (Open-Meteo geocoding — free, no key). Offline search always works.
enum OnlineGeocoder {
    struct Response: Decodable { let results: [Result]? }
    struct Result: Decodable {
        let id: Int
        let name: String
        let latitude: Double
        let longitude: Double
        let country_code: String?
        let country: String?
        let admin1: String?
        let timezone: String?
        let population: Int?
    }

    static func search(_ q: String) async -> [SavedLocation] {
        guard var c = URLComponents(string: "https://geocoding-api.open-meteo.com/v1/search") else { return [] }
        c.queryItems = [URLQueryItem(name: "name", value: q), URLQueryItem(name: "count", value: "15"),
                        URLQueryItem(name: "language", value: "en"), URLQueryItem(name: "format", value: "json")]
        guard let url = c.url else { return [] }
        do {
            var req = URLRequest(url: url); req.timeoutInterval = 8
            let (data, _) = try await URLSession.shared.data(for: req)
            let r = try JSONDecoder().decode(Response.self, from: data)
            return (r.results ?? []).compactMap { x in
                let tz = x.timezone ?? TimeZoneResolver.resolve(latitude: x.latitude, longitude: x.longitude).timeZoneId
                guard TimeZone(identifier: tz) != nil else { return nil }
                return SavedLocation(name: x.name, latitude: x.latitude, longitude: x.longitude, timeZoneId: tz, source: .online,
                                     admin1: x.admin1 ?? "", countryCode: x.country_code ?? "", countryName: x.country ?? "", geonameId: x.id)
            }
        } catch { return [] }
    }
}

struct CitySearchView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) var dismiss
    let onPick: (SavedLocation) -> Void

    @State private var query = ""
    @State private var offline: [City] = []
    @State private var online: [SavedLocation] = []
    @State private var loadingOnline = false
    @State private var task: Task<Void, Never>? = nil

    var body: some View {
        NavigationStack {
            List {
                if query.trimmingCharacters(in: .whitespaces).isEmpty {
                    Section("Popular cities") { ForEach(CityDatabase.shared.popular()) { cityRow($0) } }
                } else {
                    if offline.isEmpty && online.isEmpty && !loadingOnline {
                        Text("No matches. Try another spelling, or enter coordinates.").foregroundStyle(.secondary)
                    }
                    if !offline.isEmpty { Section("Cities") { ForEach(offline) { cityRow($0) } } }
                    if model.settings.onlineSearch {
                        Section {
                            if loadingOnline { HStack { ProgressView(); Text("Searching online…").foregroundStyle(.secondary) } }
                            ForEach(online, id: \.id) { l in
                                Button { onPick(l) } label: { placeRow(l.name, l.subtitle, l.zone) }
                            }
                        } header: { Text("More places (online)") } footer: {
                            Text("Online search sends only what you type to open-meteo.com. Turn it off in Settings.")
                        }
                    }
                }
            }
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "City, town or region")
            .onChange(of: query) { _, q in run(q) }
            .navigationTitle("Find a place")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
        }
    }

    private func cityRow(_ c: City) -> some View {
        Button { onPick(c.toLocation()) } label: { placeRow(c.name, c.region, TimeZone(identifier: c.timeZoneId)) }
    }

    private func placeRow(_ name: String, _ region: String, _ zone: TimeZone?) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(name).foregroundStyle(.primary)
                Text(region).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            if let z = zone {
                Text(model.formatter.time(Date(), z)).font(.subheadline).monospacedDigit().foregroundStyle(.secondary)
            }
        }
    }

    private func run(_ q: String) {
        task?.cancel()
        let trimmed = q.trimmingCharacters(in: .whitespaces)
        offline = trimmed.isEmpty ? [] : CityDatabase.shared.search(trimmed)
        online = []
        guard model.settings.onlineSearch, trimmed.count >= 3 else { loadingOnline = false; return }
        loadingOnline = true
        task = Task {
            try? await Task.sleep(nanoseconds: 450_000_000)
            if Task.isCancelled { return }
            let found = await OnlineGeocoder.search(trimmed)
            if Task.isCancelled { return }
            let known = offline
            online = found.filter { f in !known.contains { abs($0.latitude - f.latitude) < 0.05 && abs($0.longitude - f.longitude) < 0.05 } }
            loadingOnline = false
        }
    }
}

struct CoordinatesView: View {
    @Environment(\.dismiss) var dismiss
    let onPick: (SavedLocation) -> Void
    @State private var lat = ""
    @State private var lon = ""
    @State private var label = ""
    @State private var zoneId = ""

    private var latV: Double? { parse(lat) }
    private var lonV: Double? { parse(lon) }
    private var valid: Bool { (latV.map { abs($0) <= 90 } ?? false) && (lonV.map { abs($0) <= 180 } ?? false) }
    private var resolved: ResolvedPlace? { valid ? TimeZoneResolver.resolve(latitude: latV!, longitude: lonV!) : nil }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Latitude (e.g. 42.3314 or 42.3314 N)", text: $lat).keyboardType(.numbersAndPunctuation)
                    TextField("Longitude (e.g. -83.0458 or 83.0458 W)", text: $lon).keyboardType(.numbersAndPunctuation)
                } footer: { Text("Use decimal degrees. South and West are negative, or add S / W.") }
                Section("Label (optional)") { TextField("e.g. Cabin, Job site", text: $label) }
                if let r = resolved {
                    Section("Detected") {
                        LabeledRow(label: "Nearest place", value: r.nearestCity.map { "\($0.name), \($0.countryName)" } ?? "Open water")
                        if let d = r.distanceKm { LabeledRow(label: "Distance", value: String(format: "%.0f km", d)) }
                        Picker("Time zone", selection: $zoneId) {
                            Text("\(r.timeZoneId) (detected)").tag("")
                            ForEach(TimeZone.knownTimeZoneIdentifiers, id: \.self) { Text($0).tag($0) }
                        }
                        if !r.confident { Text("Far from any city — please check the time zone.").font(.caption).foregroundStyle(.orange) }
                    }
                }
            }
            .navigationTitle("Coordinates")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {
                        guard let r = resolved, let la = latV, let lo = lonV else { return }
                        var l = SavedLocation(name: r.name, latitude: la, longitude: lo, timeZoneId: zoneId.isEmpty ? r.timeZoneId : zoneId, source: .manual,
                                              admin1: r.admin1, countryCode: r.countryCode, countryName: r.countryName)
                        if !label.trimmingCharacters(in: .whitespaces).isEmpty { l.customLabel = label.trimmingCharacters(in: .whitespaces) }
                        onPick(l)
                    }
                    .disabled(!valid)
                }
            }
        }
    }

    private func parse(_ s: String) -> Double? {
        var t = s.trimmingCharacters(in: .whitespaces).uppercased().replacingOccurrences(of: "°", with: "").replacingOccurrences(of: ",", with: ".")
        var sign = 1.0
        if let last = t.last, "NSEW".contains(last) { if last == "S" || last == "W" { sign = -1 }; t.removeLast(); t = t.trimmingCharacters(in: .whitespaces) }
        return Double(t).map { $0 * sign }
    }
}

struct LocationDetailView: View {
    @EnvironmentObject var model: AppModel
    let id: String
    @Environment(\.dismiss) var dismiss
    @State private var renaming = false
    @State private var newName = ""

    var body: some View {
        if let loc = model.locations.first(where: { $0.id == id }) { content(loc) } else { Text("Removed").foregroundStyle(.secondary) }
    }

    private func content(_ loc: SavedLocation) -> some View {
        TimelineView(.everyMinute) { ctx in
            let snap = SolarService.shared.snapshot(loc, at: ctx.date)
            let fmt = model.formatter
            List {
                Section {
                    LabeledRow(label: "Local time", value: fmt.time(ctx.date, loc.zone))
                    LabeledRow(label: "Time zone", value: "\(loc.timeZoneId) · \(TimeFormatter.utcOffset(loc.zone, at: ctx.date))")
                    LabeledRow(label: "Coordinates", value: "\(formatCoordinate(loc.latitude, latitude: true))\n\(formatCoordinate(loc.longitude, latitude: false))")
                }
                Section("Today") {
                    LabeledRow(label: "Sunrise", value: fmt.time(snap.today.sunrise, loc.zone))
                    LabeledRow(label: "Sunset", value: fmt.time(snap.today.sunset, loc.zone))
                    LabeledRow(label: "Day length", value: Durations.hm(snap.today.daylight))
                    LabeledRow(label: "Solar noon", value: fmt.time(snap.today.solarNoon, loc.zone))
                }
                Section {
                    Button { model.setActive(loc.id) } label: { Label("Show on Today", systemImage: "sun.horizon") }
                    Button { model.setDefault(loc.id) } label: { Label(loc.isDefault ? "Default place" : "Make default (used by widgets & reminders)", systemImage: "star.circle") }
                        .disabled(loc.isDefault)
                    Button { model.setFavorite(loc.id, !loc.isFavorite) } label: { Label(loc.isFavorite ? "Remove from favorites" : "Add to favorites", systemImage: loc.isFavorite ? "heart.slash" : "heart") }
                    Button { newName = loc.customLabel ?? ""; renaming = true } label: { Label("Rename", systemImage: "pencil") }
                    if loc.source != .device {
                        Button(role: .destructive) { dismiss(); model.delete(loc.id) } label: { Label("Remove", systemImage: "trash") }
                    }
                }
            }
        }
        .navigationTitle(loc.displayName)
        .alert("Rename", isPresented: $renaming) {
            TextField(loc.name, text: $newName)
            Button("Save") { model.rename(loc.id, newName.trimmingCharacters(in: .whitespaces).isEmpty ? nil : newName) }
            Button("Cancel", role: .cancel) {}
        } message: { Text("Leave empty to use the original name.") }
    }
}
