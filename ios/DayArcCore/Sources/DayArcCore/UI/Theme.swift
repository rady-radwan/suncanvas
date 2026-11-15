import SwiftUI
import CoreText

public extension ARGB {
    var color: Color { Color(.sRGB, red: r, green: g, blue: b, opacity: a) }
    func opacity(_ f: Double) -> Color { Color(.sRGB, red: r, green: g, blue: b, opacity: a * max(0, min(1, f))) }

    /// WCAG relative luminance.
    var luminance: Double {
        func ch(_ s: Double) -> Double { s <= 0.03928 ? s / 12.92 : pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)
    }
    static func contrast(_ x: ARGB, _ y: ARGB) -> Double {
        let a = x.luminance, b = y.luminance
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }
    /// Composite self (with alpha) over an opaque colour.
    func over(_ bg: ARGB) -> ARGB {
        ARGB(r: r * a + bg.r * (1 - a), g: g * a + bg.g * (1 - a), b: b * a + bg.b * (1 - a))
    }
}

public extension Color {
    init(hex: UInt32) { self = ARGB(hex).color }
}

/// Bundled fonts (SIL OFL), registered at runtime so both the app and the widget extension can use them.
public enum FontRegistry {
    private static var done = false
    private static let lock = NSLock()

    public static func registerAll() {
        lock.lock(); defer { lock.unlock() }
        if done { return }
        done = true
        guard let dir = Bundle.module.url(forResource: "Fonts", withExtension: nil),
              let files = try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil) else { return }
        for f in files where f.pathExtension == "ttf" {
            CTFontManagerRegisterFontsForURL(f as CFURL, .process, nil)
        }
    }

    /// PostScript names of the bundled files.
    public static func postScriptName(_ font: WidgetFont, _ weight: FontWeightChoice) -> String {
        switch font {
        case .inter: return ["Inter-Light", "Inter-Regular", "Inter-Bold"][weight.index]
        case .nunito: return ["NunitoExtraLight-Light", "NunitoExtraLight-Regular", "NunitoExtraLight-ExtraBold"][weight.index]
        case .poppins: return ["Poppins-Light", "Poppins-Regular", "Poppins-SemiBold"][weight.index]
        case .spaceGrotesk: return ["SpaceGroteskLight-Light", "SpaceGroteskLight-Regular", "SpaceGroteskLight-Bold"][weight.index]
        case .playfair: return ["PlayfairDisplay-Regular", "PlayfairDisplay-Regular", "PlayfairDisplay-Bold"][weight.index]
        case .cormorant: return ["CormorantGaramondLight-Light", "CormorantGaramondLight-Regular", "CormorantGaramondLight-Bold"][weight.index]
        case .jetbrainsMono: return ["JetBrainsMono-Light", "JetBrainsMono-Regular", "JetBrainsMono-Bold"][weight.index]
        case .orbitron: return ["Orbitron-Regular", "Orbitron-Regular", "Orbitron-Bold"][weight.index]
        case .oswald: return ["Oswald-Light", "Oswald-Regular", "Oswald-SemiBold"][weight.index]
        case .bebas: return "BebasNeue-Regular"
        case .quicksand: return ["QuicksandLight-Light", "QuicksandLight-Regular", "QuicksandLight-Bold"][weight.index]
        }
    }

    public static func font(_ font: WidgetFont, _ weight: FontWeightChoice, size: CGFloat) -> Font {
        registerAll()
        return .custom(postScriptName(font, weight), fixedSize: size)
    }
}

extension FontWeightChoice {
    var index: Int { self == .light ? 0 : self == .regular ? 1 : 2 }
}

/// App design system (matches Android): navy nights, warm-white days, golden amber accents.
public enum Palette {
    public static let navy950 = Color(hex: 0xFF070B1C)
    public static let navy900 = Color(hex: 0xFF0B1026)
    public static let navy850 = Color(hex: 0xFF10173A)
    public static let navy800 = Color(hex: 0xFF151E46)
    public static let warmWhite = Color(hex: 0xFFFFF9F2)
    public static let sand50 = Color(hex: 0xFFFBF1E4)
    public static let amber = Color(hex: 0xFFFFB547)
    public static let amberDeep = Color(hex: 0xFFB86A00)
    public static let sunset = Color(hex: 0xFFFF7A3D)
    public static let softBlue = Color(hex: 0xFF7FB2FF)
    public static let twilight = Color(hex: 0xFF8E6CFF)
    public static let golden = Color(hex: 0xFFFFC870)
}
