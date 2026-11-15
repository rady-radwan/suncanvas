import SwiftUI
import Charts
import DayArcCore

enum CalMode: String, CaseIterable, Identifiable { case day = "Day", week = "Week", month = "Month", year = "Year", compare = "Compare"; var id: String { rawValue } }

/// One day reduced to chart-friendly numbers (hours after local midnight).
struct DayPoint: Identifiable {
    let id: LocalDate
    let day: SolarDay
    let rise: Double?
    let set: Double?
    let lengthH: Double
    init(_ d: SolarDay) {
        id = d.date; day = d
        let start = d.date.start(in: d.zone)
        rise = d.sunrise.map { $0.timeIntervalSince(start) / 3600 }
        set = d.sunset.map { $0.timeIntervalSince(start) / 3600 }
        lengthH = d.dayLength / 3600
    }
    var chartDate: Date { day.date.dateAtNoonUTC }
}

struct CalendarScreen: View {
    @EnvironmentObject var model: AppModel
    @State private var mode: CalMode = .month
    @State private var picked = Date()

    var body: some View {
        NavigationStack {
            Group {
                if let loc = model.active {
                    ScrollView {
                        VStack(spacing: 12) {
                            Picker("Range", selection: $mode) { ForEach(CalMode.allCases) { Text($0.rawValue).tag($0) } }
                                .pickerStyle(.segmented)
                            if mode != .compare {
                                DatePicker("Date", selection: $picked, displayedComponents: .date)
                                    .datePickerStyle(.compact)
                                    .environment(\.timeZone, loc.zone)
                            }
                            let date = LocalDate(picked, in: loc.zone)
                            switch mode {
                            case .day: DayDetail(loc: loc, date: date)
                            case .week: WeekView(loc: loc, start: date)
                            case .month: MonthView(loc: loc, anchor: date) { picked = $0.start(in: loc.zone).addingTimeInterval(43_200); mode = .day }
                            case .year: YearView(loc: loc, year: date.year)
                            case .compare: CompareView(loc: loc)
                            }
                        }
                        .padding(.horizontal, DS.screen).padding(.bottom, 24)
                    }
                } else {
                    ContentUnavailableView("Choose a place first", systemImage: "calendar", description: Text("Pick a city on the Today tab."))
                }
            }
            .navigationTitle("Calendar")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) { Button("Today") { picked = Date() } }
            }
        }
    }
}

// MARK: - Day

struct DayDetail: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.colorScheme) var scheme
    let loc: SavedLocation
    let date: LocalDate

    var body: some View {
        let day = SolarService.shared.day(loc, date)
        let curve = SolarService.shared.curve(loc, date)
        let start = date.start(in: loc.zone)
        let fmt = model.formatter
        VStack(spacing: 12) {
            Card {
                VStack(alignment: .leading, spacing: 8) {
                    Text(fmt.date(date)).font(.headline)
                    Chart {
                        RuleMark(y: .value("Horizon", 0)).foregroundStyle(Color.secondary.opacity(0.5))
                        ForEach(curve.indices, id: \.self) { i in
                            AreaMark(x: .value("Hour", curve[i].0.timeIntervalSince(start) / 3600), yStart: .value("Base", -20), yEnd: .value("Elevation", max(-20, curve[i].1)))
                                .foregroundStyle(LinearGradient(colors: [Palette.golden.opacity(0.35), .clear], startPoint: .top, endPoint: .bottom))
                            LineMark(x: .value("Hour", curve[i].0.timeIntervalSince(start) / 3600), y: .value("Elevation", curve[i].1))
                                .foregroundStyle(DS.sunrise(scheme))
                        }
                    }
                    .chartXScale(domain: 0...24)
                    .chartXAxis { AxisMarks(values: [0, 6, 12, 18, 24]) { v in AxisGridLine(); AxisValueLabel { Text(hourLabel(v.as(Double.self) ?? 0)) } } }
                    .chartYAxis { AxisMarks { v in AxisGridLine(); AxisValueLabel { Text("\(v.as(Double.self).map { Int($0) } ?? 0)°") } } }
                    .frame(height: 180)
                    .accessibilityLabel("Sun elevation through the day")
                }
            }
            Card {
                VStack(spacing: 0) {
                    ForEach(day.events, id: \.date) { e in
                        LabeledRow(label: e.type.label, value: fmt.time(e.date, loc.zone))
                    }
                    Divider().padding(.vertical, 4)
                    LabeledRow(label: "Day length", value: Durations.hm(day.dayLength))
                    LabeledRow(label: "Noon elevation", value: String(format: "%.1f°", day.noonElevation))
                    let prev = SolarService.shared.day(loc, date.adding(days: -1))
                    LabeledRow(label: "Change vs previous day", value: Durations.signedMinSec(day.daylight - prev.daylight))
                }
            }
        }
    }

    private func hourLabel(_ h: Double) -> String {
        let i = Int(h) % 24
        if model.settings.use24h { return String(format: "%02d", i) }
        return i == 0 ? "12a" : i == 12 ? "12p" : i < 12 ? "\(i)a" : "\(i - 12)p"
    }
}

// MARK: - Week

struct WeekView: View {
    @EnvironmentObject var model: AppModel
    let loc: SavedLocation
    let start: LocalDate

    var body: some View {
        let first = start.adding(days: -(start.weekday - 1))
        let days = SolarService.shared.range(loc, from: first, days: 7)
        let fmt = model.formatter
        VStack(spacing: 12) {
            Card {
                DaylightBandChart(points: days.map(DayPoint.init)).frame(height: 200)
            }
            Card {
                VStack(spacing: 0) {
                    HStack { Text("Day").frame(width: 70, alignment: .leading); Spacer(); Text("Sunrise"); Spacer(); Text("Sunset"); Spacer(); Text("Length") }
                        .font(.caption.weight(.semibold)).foregroundStyle(.secondary).padding(.bottom, 6)
                    ForEach(days, id: \.date) { d in
                        HStack {
                            Text("\(fmt.weekday(d.date)) \(d.date.day)").frame(width: 70, alignment: .leading)
                                .fontWeight(d.date == LocalDate(Date(), in: loc.zone) ? .bold : .regular)
                            Spacer(); Text(fmt.time(d.sunrise, loc.zone)); Spacer(); Text(fmt.time(d.sunset, loc.zone)); Spacer(); Text(Durations.hm(d.dayLength))
                        }
                        .font(.subheadline).monospacedDigit().padding(.vertical, 5)
                    }
                }
            }
        }
    }
}

struct DaylightBandChart: View {
    @Environment(\.colorScheme) var scheme
    let points: [DayPoint]
    var yearly = false

    var body: some View {
        let axisFormat: Date.FormatStyle = yearly ? .dateTime.month(.narrow) : .dateTime.weekday(.abbreviated)
        Chart(points) { p in
            if let r = p.rise, let s = p.set {
                BarMark(x: .value("Date", p.chartDate, unit: .day), yStart: .value("Sunrise", r), yEnd: .value("Sunset", s))
                    .foregroundStyle(LinearGradient(colors: [DS.sunrise(scheme), DS.sunset(scheme)], startPoint: .bottom, endPoint: .top))
                    .cornerRadius(3)
            } else if p.day.dayType == .polarDay {
                BarMark(x: .value("Date", p.chartDate, unit: .day), yStart: .value("Sunrise", 0), yEnd: .value("Sunset", 24))
                    .foregroundStyle(Palette.golden.opacity(0.6))
            }
        }
        .chartYScale(domain: 0...24)
        .chartYAxis { AxisMarks(values: [0, 6, 12, 18, 24]) { v in AxisGridLine(); AxisValueLabel { Text("\(v.as(Int.self) ?? 0)h") } } }
        .chartXAxis { AxisMarks(values: .stride(by: yearly ? Calendar.Component.month : Calendar.Component.day)) { _ in AxisGridLine(); AxisValueLabel(format: axisFormat) } }
        .environment(\.timeZone, TimeZone(identifier: "UTC")!)
        .accessibilityLabel("Daylight from sunrise to sunset for each day")
    }
}

// MARK: - Month

struct MonthView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.colorScheme) var scheme
    let loc: SavedLocation
    let anchor: LocalDate
    let onPick: (LocalDate) -> Void

    var body: some View {
        let first = LocalDate(year: anchor.year, month: anchor.month, day: 1)
        let days = SolarService.shared.range(loc, from: first, days: first.daysInMonth)
        let maxLen = days.map(\.dayLength).max() ?? 1, minLen = days.map(\.dayLength).min() ?? 0
        let today = LocalDate(Date(), in: loc.zone)
        let fmt = model.formatter
        VStack(spacing: 12) {
            Card {
                VStack(spacing: 8) {
                    Text(fmt.monthYear(first)).font(.headline).frame(maxWidth: .infinity, alignment: .leading)
                    let cols = Array(repeating: GridItem(.flexible(), spacing: 4), count: 7)
                    LazyVGrid(columns: cols, spacing: 4) {
                        ForEach(["M", "T", "W", "T", "F", "S", "S"].indices, id: \.self) { i in
                            Text(["M", "T", "W", "T", "F", "S", "S"][i]).font(.caption2.weight(.semibold)).foregroundStyle(.secondary)
                        }
                        ForEach(0..<(first.weekday - 1), id: \.self) { _ in Color.clear.frame(height: 52) }
                        ForEach(days, id: \.date) { d in
                            let t = maxLen > minLen ? (d.dayLength - minLen) / (maxLen - minLen) : 0.5
                            Button { onPick(d.date) } label: {
                                VStack(spacing: 1) {
                                    Text("\(d.date.day)").font(.subheadline.weight(d.date == today ? .bold : .regular))
                                    Text(fmt.time(d.sunrise, loc.zone).replacingOccurrences(of: " AM", with: "a")).font(.system(size: 8)).foregroundStyle(.secondary)
                                    Text(fmt.time(d.sunset, loc.zone).replacingOccurrences(of: " PM", with: "p")).font(.system(size: 8)).foregroundStyle(.secondary)
                                }
                                .frame(maxWidth: .infinity, minHeight: 52)
                                .background(RoundedRectangle(cornerRadius: 8).fill(Palette.golden.opacity(0.08 + 0.32 * t)))
                                .overlay(RoundedRectangle(cornerRadius: 8).stroke(d.date == today ? Palette.amber : .clear, lineWidth: 1.5))
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("\(fmt.date(d.date)), sunrise \(fmt.time(d.sunrise, loc.zone)), sunset \(fmt.time(d.sunset, loc.zone))")
                        }
                    }
                    Text("Darker days have more daylight. Tap a day for details.").font(.caption2).foregroundStyle(.secondary)
                }
            }
            Card {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Sunrise & sunset this month").font(.subheadline.weight(.semibold))
                    Chart(days.map(DayPoint.init)) { p in
                        if let r = p.rise { LineMark(x: .value("Day", p.id.day), y: .value("Hour", r), series: .value("Event", "Sunrise")).foregroundStyle(DS.sunrise(scheme)) }
                        if let s = p.set { LineMark(x: .value("Day", p.id.day), y: .value("Hour", s), series: .value("Event", "Sunset")).foregroundStyle(DS.sunset(scheme)) }
                    }
                    .chartYAxis { AxisMarks { v in AxisGridLine(); AxisValueLabel { Text(String(format: "%.0fh", v.as(Double.self) ?? 0)) } } }
                    .frame(height: 170)
                    let longest = days.max { $0.dayLength < $1.dayLength }, shortest = days.min { $0.dayLength < $1.dayLength }
                    if let l = longest, let s = shortest {
                        LabeledRow(label: "Longest day", value: "\(fmt.shortDate(l.date)) · \(Durations.hm(l.dayLength))")
                        LabeledRow(label: "Shortest day", value: "\(fmt.shortDate(s.date)) · \(Durations.hm(s.dayLength))")
                    }
                }
            }
        }
    }
}

// MARK: - Year

struct YearView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.colorScheme) var scheme
    let loc: SavedLocation
    let year: Int
    @State private var points: [DayPoint] = []

    var body: some View {
        let fmt = model.formatter
        VStack(spacing: 12) {
            Card {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Daylight in \(String(year))").font(.headline)
                    if points.isEmpty { ProgressView().frame(maxWidth: .infinity, minHeight: 200) } else {
                        DaylightBandChart(points: points, yearly: true).frame(height: 220)
                    }
                }
            }
            Card {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Day length").font(.subheadline.weight(.semibold))
                    Chart(points) { p in
                        AreaMark(x: .value("Date", p.chartDate), y: .value("Hours", p.lengthH))
                            .foregroundStyle(LinearGradient(colors: [Palette.golden.opacity(0.5), Palette.golden.opacity(0.05)], startPoint: .top, endPoint: .bottom))
                        LineMark(x: .value("Date", p.chartDate), y: .value("Hours", p.lengthH)).foregroundStyle(DS.sunrise(scheme))
                    }
                    .chartYScale(domain: 0...24)
                    .chartXAxis { AxisMarks(values: .stride(by: .month)) { _ in AxisGridLine(); AxisValueLabel(format: .dateTime.month(.narrow)) } }
                    .environment(\.timeZone, TimeZone(identifier: "UTC")!)
                    .frame(height: 170)
                    if let l = points.max(by: { $0.lengthH < $1.lengthH }), let s = points.min(by: { $0.lengthH < $1.lengthH }) {
                        LabeledRow(label: "Longest day", value: "\(fmt.shortDate(l.id)) · \(Durations.hm(l.day.dayLength))")
                        LabeledRow(label: "Shortest day", value: "\(fmt.shortDate(s.id)) · \(Durations.hm(s.day.dayLength))")
                        let er = points.compactMap { p in p.rise.map { (p, $0) } }.min { $0.1 < $1.1 }
                        let ls = points.compactMap { p in p.set.map { (p, $0) } }.max { $0.1 < $1.1 }
                        if let e = er { LabeledRow(label: "Earliest sunrise", value: "\(fmt.shortDate(e.0.id)) · \(fmt.time(e.0.day.sunrise, loc.zone))") }
                        if let x = ls { LabeledRow(label: "Latest sunset", value: "\(fmt.shortDate(x.0.id)) · \(fmt.time(x.0.day.sunset, loc.zone))") }
                    }
                }
            }
            Card {
                VStack(spacing: 0) {
                    ForEach(Seasons.events(year: year), id: \.date) { e in
                        LabeledRow(label: e.type.label, value: "\(fmt.date(e.date, loc.zone)) · \(fmt.time(e.date, loc.zone))")
                    }
                }
            }
        }
        .task(id: "\(loc.id)|\(year)") {
            let l = loc, y = year
            points = []
            points = await Task.detached(priority: .userInitiated) { () -> [DayPoint] in
                let first = LocalDate(year: y, month: 1, day: 1)
                let n = LocalDate(year: y, month: 12, day: 31) == first.adding(days: 365) ? 366 : 365
                return SolarService.shared.range(l, from: first, days: n).map(DayPoint.init)
            }.value
        }
    }
}

// MARK: - Compare

struct CompareView: View {
    @EnvironmentObject var model: AppModel
    let loc: SavedLocation
    @State private var a = Date()
    @State private var b = Calendar.current.date(byAdding: .month, value: 6, to: Date()) ?? Date()
    @State private var otherCity: String? = nil

    var body: some View {
        let fmt = model.formatter
        let l2 = otherCity.flatMap { id in model.locations.first { $0.id == id } } ?? loc
        let d1 = SolarService.shared.day(loc, LocalDate(a, in: loc.zone))
        let d2 = SolarService.shared.day(l2, LocalDate(b, in: l2.zone))
        VStack(spacing: 12) {
            Card {
                VStack(spacing: 8) {
                    DatePicker("First date (\(loc.displayName))", selection: $a, displayedComponents: .date)
                    DatePicker("Second date", selection: $b, displayedComponents: .date)
                    Picker("Second place", selection: $otherCity) {
                        Text("Same place").tag(String?.none)
                        ForEach(model.cityList) { Text($0.displayName).tag(String?.some($0.id)) }
                    }
                }
            }
            Card {
                VStack(spacing: 0) {
                    HStack { Text(""); Spacer(); Text(fmt.shortDate(d1.date)).frame(width: 96); Text(fmt.shortDate(d2.date)).frame(width: 96) }
                        .font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                    row("Sunrise", fmt.time(d1.sunrise, loc.zone), fmt.time(d2.sunrise, l2.zone))
                    row("Sunset", fmt.time(d1.sunset, loc.zone), fmt.time(d2.sunset, l2.zone))
                    row("Solar noon", fmt.time(d1.solarNoon, loc.zone), fmt.time(d2.solarNoon, l2.zone))
                    row("Day length", Durations.hm(d1.dayLength), Durations.hm(d2.dayLength))
                    row("Noon elevation", String(format: "%.1f°", d1.noonElevation), String(format: "%.1f°", d2.noonElevation))
                    Divider().padding(.vertical, 6)
                    let diff = d2.dayLength - d1.dayLength
                    LabeledRow(label: "Difference", value: abs(diff) < 60 ? "About the same" : (diff > 0 ? "+" : "−") + Durations.hm(diff) + " daylight")
                }
            }
        }
    }

    private func row(_ k: String, _ x: String, _ y: String) -> some View {
        HStack { Text(k).foregroundStyle(.secondary); Spacer(); Text(x).frame(width: 96); Text(y).frame(width: 96) }
            .font(.subheadline).monospacedDigit().padding(.vertical, 5)
    }
}
