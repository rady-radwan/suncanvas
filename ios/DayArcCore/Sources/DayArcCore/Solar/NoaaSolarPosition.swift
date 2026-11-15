import Foundation

/// Solar position using the NOAA Solar Calculator equations (after Meeus, "Astronomical Algorithms").
/// Swift port of the Kotlin `core-solar` engine used by the Android app — identical results.
/// Angles are degrees; times are epoch milliseconds (UTC).
public enum NoaaSolarPosition {

    /// Standard altitude for sunrise/sunset: refraction (34') + solar semi-diameter (16').
    public static let sunriseAltitude = -0.833

    public struct Ephemeris {
        public let declination: Double
        public let equationOfTime: Double
        public let apparentLongitude: Double
    }

    @inline(__always) static func rad(_ d: Double) -> Double { d * .pi / 180 }
    @inline(__always) static func deg(_ r: Double) -> Double { r * 180 / .pi }
    @inline(__always) static func norm360(_ v: Double) -> Double { v - 360 * floor(v / 360) }

    public static func julianDay(_ ms: Int64) -> Double { Double(ms) / 86_400_000.0 + 2440587.5 }

    public static func ephemeris(_ ms: Int64) -> Ephemeris {
        let t = (julianDay(ms) - 2451545.0) / 36525.0
        let l0 = norm360(280.46646 + t * (36000.76983 + t * 0.0003032))
        let m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        let e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        let mR = rad(m)
        let c = sin(mR) * (1.914602 - t * (0.004817 + 0.000014 * t))
            + sin(2 * mR) * (0.019993 - 0.000101 * t)
            + sin(3 * mR) * 0.000289
        let trueLong = l0 + c
        let omega = 125.04 - 1934.136 * t
        let appLong = trueLong - 0.00569 - 0.00478 * sin(rad(omega))
        let meanObliq = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        let obliqCorr = meanObliq + 0.00256 * cos(rad(omega))
        let decl = deg(asin(sin(rad(obliqCorr)) * sin(rad(appLong))))
        let yt = tan(rad(obliqCorr) / 2)
        let y = yt * yt
        let l0r = rad(l0)
        let a1 = y * sin(2 * l0r)
        let a2 = 2 * e * sin(mR)
        let a3 = 4 * e * y * sin(mR) * cos(2 * l0r)
        let a4 = 0.5 * y * y * sin(4 * l0r)
        let a5 = 1.25 * e * e * sin(2 * mR)
        let eqTime = 4.0 * deg(a1 - a2 + a3 - a4 - a5)
        return Ephemeris(declination: decl, equationOfTime: eqTime, apparentLongitude: norm360(appLong))
    }

    /// Local hour angle in [-180, 180): 0 at solar noon, negative in the morning.
    public static func hourAngle(_ ms: Int64, longitude: Double, eph: Ephemeris? = nil) -> Double {
        let ep = eph ?? ephemeris(ms)
        let dayMs: Int64 = 86_400_000
        let minutesOfDay = Double(((ms % dayMs) + dayMs) % dayMs) / 60_000.0
        var tst = minutesOfDay + ep.equationOfTime + 4.0 * longitude
        tst = tst - 1440.0 * floor(tst / 1440.0)
        var ha = tst / 4.0 - 180.0
        if ha < -180 { ha += 360 }
        if ha >= 180 { ha -= 360 }
        return ha
    }

    /// Geometric (unrefracted) elevation of the solar centre.
    public static func geometricElevation(_ ms: Int64, latitude: Double, longitude: Double) -> Double {
        let eph = ephemeris(ms)
        return elevation(latitude: latitude, declination: eph.declination, hourAngle: hourAngle(ms, longitude: longitude, eph: eph))
    }

    public static func position(_ ms: Int64, latitude: Double, longitude: Double) -> SunPosition {
        let eph = ephemeris(ms)
        let ha = hourAngle(ms, longitude: longitude, eph: eph)
        let el = elevation(latitude: latitude, declination: eph.declination, hourAngle: ha)
        let latR = rad(latitude), decR = rad(eph.declination), haR = rad(ha)
        let az = norm360(deg(atan2(sin(haR), cos(haR) * sin(latR) - tan(decR) * cos(latR))) + 180)
        return SunPosition(elevation: el + refraction(el), geometricElevation: el, azimuth: az,
                           declination: eph.declination, equationOfTimeMinutes: eph.equationOfTime, hourAngle: ha)
    }

    /// NOAA approximation of atmospheric refraction (degrees).
    public static func refraction(_ elevation: Double) -> Double {
        if elevation > 85 { return 0 }
        let te = tan(rad(elevation))
        let arcSec: Double
        if elevation > 5 {
            arcSec = 58.1 / te - 0.07 / (te * te * te) + 0.000086 / (te * te * te * te * te)
        } else if elevation > -0.575 {
            arcSec = 1735.0 + elevation * (-518.2 + elevation * (103.4 + elevation * (-12.79 + elevation * 0.711)))
        } else {
            arcSec = -20.772 / te
        }
        return arcSec / 3600.0
    }

    static func elevation(latitude: Double, declination: Double, hourAngle: Double) -> Double {
        let latR = rad(latitude), decR = rad(declination)
        let cosZ = max(-1.0, min(1.0, sin(latR) * sin(decR) + cos(latR) * cos(decR) * cos(rad(hourAngle))))
        return 90.0 - deg(acos(cosZ))
    }
}

public struct SunPosition: Equatable {
    /// Apparent elevation including refraction.
    public let elevation: Double
    public let geometricElevation: Double
    /// Clockwise from true north, [0, 360).
    public let azimuth: Double
    public let declination: Double
    public let equationOfTimeMinutes: Double
    public let hourAngle: Double
}
