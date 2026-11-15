import XCTest
@testable import DayArcCore

/// Same reference data as the Android engine: Skyfield + JPL DE421, 280 location/date pairs.
final class SolarTests: XCTestCase {

    struct Row { let id: String; let lat: Double; let lon: Double; let zone: TimeZone; let date: LocalDate; let values: [String: Date?] }

    lazy var rows: [Row] = {
        let url = Bundle.module.url(forResource: "reference_skyfield", withExtension: "csv")!
        let lines = try! String(contentsOf: url, encoding: .utf8).split(separator: "\n").map(String.init).filter { !$0.isEmpty }
        let header = lines[0].split(separator: ",").map(String.init)
        return lines.dropFirst().map { line in
            let c = line.split(separator: ",", omittingEmptySubsequences: false).map(String.init)
            let zone = TimeZone(identifier: c[3])!
            let dp = c[4].split(separator: "-").map { Int($0)! }
            var values: [String: Date?] = [:]
            for i in 5..<header.count {
                values[header[i]] = c[i] == "none" ? nil : Self.parseLocal(c[i], zone)
            }
            return Row(id: c[0], lat: Double(c[1])!, lon: Double(c[2])!, zone: zone, date: LocalDate(year: dp[0], month: dp[1], day: dp[2]), values: values)
        }
    }()

    static func parseLocal(_ s: String, _ zone: TimeZone) -> Date {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = zone
        f.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        return f.date(from: s)!
    }

    let calc = SolarCalculator()

    func check(_ name: String, _ expected: Date??, _ actual: Date?, tolerance: TimeInterval, _ failures: inout [String], _ label: String) {
        let exp: Date? = expected ?? nil
        if exp == nil && actual == nil { return }
        guard let e = exp, let a = actual else { failures.append("\(label) \(name) expected=\(String(describing: exp)) actual=\(String(describing: actual))"); return }
        let diff = abs(a.timeIntervalSince(e))
        if diff > tolerance { failures.append("\(label) \(name) diff=\(Int(diff))s") }
    }

    func testSunriseSunsetNoonMatchReference() {
        var failures: [String] = []
        var maxDiff: TimeInterval = 0
        for r in rows {
            let d = calc.day(r.date, latitude: r.lat, longitude: r.lon, zone: r.zone)
            let tol: TimeInterval = abs(r.lat) > 60 ? 240 : 120
            let label = "\(r.id) \(r.date)"
            check("sunrise", r.values["sunrise"], d.sunrise, tolerance: tol, &failures, label)
            check("sunset", r.values["sunset"], d.sunset, tolerance: tol, &failures, label)
            check("noon", r.values["noon"], d.solarNoon, tolerance: 60, &failures, label)
            if let e = r.values["sunrise"] ?? nil, let a = d.sunrise { maxDiff = max(maxDiff, abs(a.timeIntervalSince(e))) }
        }
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: "\n"))
        XCTAssertLessThan(maxDiff, 30, "sunrise should match the JPL reference within seconds")
    }

    func testTwilightMatchesReference() {
        var failures: [String] = []
        for r in rows where abs(r.lat) <= 60 {
            let d = calc.day(r.date, latitude: r.lat, longitude: r.lon, zone: r.zone)
            let label = "\(r.id) \(r.date)"
            check("civilDawn", r.values["civilDawn"], d.civilDawn, tolerance: 120, &failures, label)
            check("civilDusk", r.values["civilDusk"], d.civilDusk, tolerance: 120, &failures, label)
            check("astroDawn", r.values["astroDawn"], d.astronomicalDawn, tolerance: 180, &failures, label)
            check("astroDusk", r.values["astroDusk"], d.astronomicalDusk, tolerance: 180, &failures, label)
        }
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: "\n"))
    }

    func testPolarNightAndDay() {
        let oslo = TimeZone(identifier: "Europe/Oslo")!
        let winter = calc.day(LocalDate(year: 2026, month: 12, day: 21), latitude: 69.6492, longitude: 18.9553, zone: oslo)
        XCTAssertEqual(winter.dayType, .polarNight)
        XCTAssertNil(winter.sunrise)
        XCTAssertEqual(winter.daylight, 0)
        let summer = calc.day(LocalDate(year: 2026, month: 6, day: 21), latitude: 69.6492, longitude: 18.9553, zone: oslo)
        XCTAssertEqual(summer.dayType, .polarDay)
    }

    func testDstDayLengths() {
        let chicago = TimeZone(identifier: "America/Chicago")!
        XCTAssertEqual(calc.day(LocalDate(year: 2026, month: 3, day: 8), latitude: 32.78, longitude: -96.8, zone: chicago).dayLength, 23 * 3600)
        XCTAssertEqual(calc.day(LocalDate(year: 2026, month: 11, day: 1), latitude: 32.78, longitude: -96.8, zone: chicago).dayLength, 25 * 3600)
    }

    func testDateLine() {
        let kiri = TimeZone(identifier: "Pacific/Kiritimati")!
        let d = calc.day(LocalDate(year: 2026, month: 1, day: 1), latitude: 1.8721, longitude: -157.4278, zone: kiri)
        XCTAssertEqual(LocalDate(d.sunrise!, in: kiri), LocalDate(year: 2026, month: 1, day: 1))
    }

    func testSeasons2026() {
        let expected = ["2026-03-20T14:46:00Z", "2026-06-21T08:24:00Z", "2026-09-23T00:05:00Z", "2026-12-21T20:50:00Z"]
        let iso = ISO8601DateFormatter()
        for (e, s) in zip(Seasons.events(year: 2026), expected) {
            XCTAssertLessThan(abs(e.date.timeIntervalSince(iso.date(from: s)!)), 15 * 60, "\(e.type)")
        }
    }

    func testCityDatabaseFindsSameNameCities() {
        let paris = CityDatabase.shared.search("paris")
        XCTAssertEqual(paris.first?.countryCode, "FR")
        XCTAssertTrue(paris.contains { $0.countryCode == "US" && $0.admin1 == "Texas" })
        XCTAssertEqual(CityDatabase.shared.search("paris, tx").first?.countryCode, "US")
        XCTAssertEqual(TimeZoneResolver.resolve(latitude: 32.78, longitude: -96.80).timeZoneId, "America/Chicago")
        XCTAssertTrue(TimeZoneResolver.resolve(latitude: -30, longitude: -140).timeZoneId.hasPrefix("Etc/GMT"))
    }

    func testWidgetSpecRoundTrip() throws {
        for t in WidgetTemplates.all {
            let data = try JSONEncoder().encode(t.spec)
            let back = try JSONDecoder().decode(WidgetSpec.self, from: data)
            XCTAssertEqual(back, t.spec, t.id)
        }
        // Android-format JSON with unknown/missing keys decodes.
        let json = #"{"templateId":"minimal","name":"Old","layout":"STACK","futureField":42}"#
        let spec = try JSONDecoder().decode(WidgetSpec.self, from: Data(json.utf8))
        XCTAssertEqual(spec.name, "Old")
        XCTAssertEqual(spec.layout, .stack)
        XCTAssertGreaterThanOrEqual(WidgetTemplates.all.count, 15)
    }
}
