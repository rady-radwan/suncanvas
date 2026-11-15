import Foundation

public enum WidgetSizeClass: String, CaseIterable, Codable {
    case small, square, wide, large
    public var label: String { switch self { case .small: return "Small"; case .square: return "Small (square)"; case .wide: return "Medium"; case .large: return "Large" } }
}

public struct WidgetTemplate: Identifiable {
    public let id: String
    public let name: String
    public let description: String
    public let sizeClass: WidgetSizeClass
    public let spec: WidgetSpec
}

public struct WidgetPalette: Identifiable {
    public var id: String { name }
    public let name: String
    public let colors: ColorSpec
    public let background: ARGB
    public let background2: ARGB
    public var border: ARGB = ARGB(0xFFFFFFFF)

    public func apply(to s: WidgetSpec) -> WidgetSpec {
        var x = s
        x.colors = colors; x.background.color = background; x.background.color2 = background2; x.border.color = border
        return x
    }
}

public enum WidgetPalettes {
    public static let all: [WidgetPalette] = [
        WidgetPalette(name: "Sunrise Orange", colors: ColorSpec(0xFFFFFFFF, 0xE6FFF1E0, 0xFFFFFFFF, 0xFFFFF1E0, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFE0B2), background: ARGB(0xFFFF7A3D), background2: ARGB(0xFFFFB547)),
        WidgetPalette(name: "Golden Hour", colors: ColorSpec(0xFF2B1600, 0xCC4A2A00, 0xFF8A4B00, 0xFFB3401A, 0xFF8A4B00, 0xFF9E5800, 0xFFB3401A), background: ARGB(0xFFFFD27A), background2: ARGB(0xFFFFA94D), border: ARGB(0xFF5A3300)),
        WidgetPalette(name: "Sunset Purple", colors: ColorSpec(0xFFFFFFFF, 0xCCF3E5FF, 0xFFFFC07A, 0xFFFF7FA8, 0xFFFFB0C8, 0xFFFFC07A, 0xFFC9A6FF), background: ARGB(0xFF3B1C5A), background2: ARGB(0xFFB0457B)),
        WidgetPalette(name: "Midnight Blue", colors: ColorSpec(0xFFEEF2FF, 0xB3C7D2F5, 0xFFFFB547, 0xFFFF7A3D, 0xFF7FB2FF, 0xFFFFC14D, 0xFF7FB2FF), background: ARGB(0xFF0B1026), background2: ARGB(0xFF1B2A6B)),
        WidgetPalette(name: "Pure Black", colors: ColorSpec(0xFFFFFFFF, 0x99FFFFFF, 0xFFFFB547, 0xFFFF7A3D, 0xFFFFFFFF, 0xFFFFB547, 0xFFFFB547), background: ARGB(0xFF000000), background2: ARGB(0xFF000000)),
        WidgetPalette(name: "Pure White", colors: ColorSpec(0xFF111111, 0x99111111, 0xFFD9822B, 0xFFD9542B, 0xFF111111, 0xFFD9822B, 0xFFD9822B), background: ARGB(0xFFFFFFFF), background2: ARGB(0xFFFFFFFF), border: ARGB(0xFF000000)),
        WidgetPalette(name: "Warm Minimal", colors: ColorSpec(0xFF3A2E25, 0x993A2E25, 0xFFC7782B, 0xFFB5523A, 0xFFC7782B, 0xFFC7782B, 0xFF8C6E58), background: ARGB(0xFFF7EFE5), background2: ARGB(0xFFEADBC8), border: ARGB(0xFF3A2E25)),
        WidgetPalette(name: "Ocean Blue", colors: ColorSpec(0xFFF0FBFF, 0xB3D3F1FF, 0xFFFFD58A, 0xFFFF9E7A, 0xFF8BE0FF, 0xFFFFD58A, 0xFF8BE0FF), background: ARGB(0xFF0A3D62), background2: ARGB(0xFF0E7490)),
        WidgetPalette(name: "Desert Sand", colors: ColorSpec(0xFF3D2A14, 0x993D2A14, 0xFFB86B1F, 0xFF9C3D1C, 0xFFB86B1F, 0xFFB86B1F, 0xFF6B4E2E), background: ARGB(0xFFF2D9B3), background2: ARGB(0xFFE0B884), border: ARGB(0xFF3D2A14)),
        WidgetPalette(name: "Monochrome", colors: ColorSpec(0xFFFFFFFF, 0x99FFFFFF, 0xFFFFFFFF, 0xFFBDBDBD, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFBDBDBD), background: ARGB(0xFF1C1C1E), background2: ARGB(0xFF2C2C2E)),
        WidgetPalette(name: "Aurora", colors: ColorSpec(0xFFE9FFF7, 0xB3BDF5E3, 0xFF9CFFD9, 0xFFB28CFF, 0xFF6EF2C2, 0xFF9CFFD9, 0xFFB28CFF), background: ARGB(0xFF051C2C), background2: ARGB(0xFF0B3B3A)),
        WidgetPalette(name: "Rose Dawn", colors: ColorSpec(0xFF3A1A24, 0x993A1A24, 0xFFE07A5F, 0xFFB5446E, 0xFFE07A5F, 0xFFE07A5F, 0xFFB5446E), background: ARGB(0xFFFFE4E1), background2: ARGB(0xFFFFC9C2), border: ARGB(0xFF3A1A24)),
    ]
}

/// The 22 starting designs (same as Android).
public enum WidgetTemplates {
    static func spec(_ id: String, _ name: String, _ layout: WidgetLayout, _ edit: (inout WidgetSpec) -> Void) -> WidgetSpec {
        var s = WidgetSpec(templateId: id, name: name, layout: layout); edit(&s); return s
    }
    static var night: BackgroundSpec { var b = BackgroundSpec(); b.color = ARGB(0xFF0B1026); b.color2 = ARGB(0xFF1B2A6B); return b }
    static func bg(_ t: BackgroundType, _ edit: (inout BackgroundSpec) -> Void = { _ in }) -> BackgroundSpec { var b = BackgroundSpec(); b.type = t; edit(&b); return b }

    public static let all: [WidgetTemplate] = [
        WidgetTemplate(id: "minimal", name: "Minimal Sunrise & Sunset", description: "Two clean times with tiny icons.", sizeClass: .small, spec: spec("minimal", "Minimal", .stack) {
            $0.cornerRadius = 22; $0.background = bg(.glassDark) { $0.opacity = 0.55 }; $0.elements = [.sunrise, .sunset, .icons]
        }),
        WidgetTemplate(id: "digital", name: "Digital Clock + Sun", description: "Large live clock with today's sunrise and sunset.", sizeClass: .wide, spec: spec("digital", "Digital Clock", .clock) {
            var b = night; b.type = .gradient; b.angle = 120; $0.background = b
            $0.elements = [.time, .date, .city, .sunrise, .sunset, .icons]
            $0.typography = TypographySpec(font: .inter, clockFont: .spaceGrotesk, clockWeight: .light)
        }),
        WidgetTemplate(id: "solar_dial", name: "Circular Solar Clock", description: "A 24-hour dial with day, twilight and night, and the Sun's position.", sizeClass: .square, spec: spec("solar_dial", "Solar Dial", .solarDial) {
            $0.shape = .circle; $0.background = night; $0.elements = [.time, .sunrise, .sunset, .dayNight]
            $0.typography = TypographySpec(font: .spaceGrotesk, clockWeight: .regular)
        }),
        WidgetTemplate(id: "solar_arc", name: "Solar Arc", description: "The Sun travelling across a curved sky path.", sizeClass: .wide, spec: spec("solar_arc", "Solar Arc", .arc) {
            $0.background = bg(.liveSky); $0.elements = [.sunArc, .sunrise, .sunset, .city, .progress, .icons]
            $0.typography = TypographySpec(font: .poppins)
        }),
        WidgetTemplate(id: "horizontal_bar", name: "Elegant Horizontal Bar", description: "Sunrise ─ progress ─ sunset in one line.", sizeClass: .small, spec: spec("horizontal_bar", "Horizontal Bar", .bar) {
            $0.shape = .pill; $0.background = bg(.glassDark) { $0.opacity = 0.7 }; $0.elements = [.sunrise, .sunset, .progress, .icons]
            $0.typography = TypographySpec(font: .quicksand, weight: .bold)
        }),
        WidgetTemplate(id: "vertical_card", name: "Vertical Information Card", description: "City, date and the day's key times stacked.", sizeClass: .square, spec: spec("vertical_card", "Vertical Card", .stack) {
            $0.cornerRadius = 28; $0.background = bg(.sunset); $0.elements = [.city, .date, .sunrise, .sunset, .daylight, .icons]
            $0.align = .start; $0.typography = TypographySpec(font: .poppins)
        }),
        WidgetTemplate(id: "transparent_text", name: "Transparent Text", description: "Just text over your wallpaper, with a soft shadow.", sizeClass: .wide, spec: spec("transparent_text", "Transparent", .clock) {
            $0.shape = .textOnly; $0.background = bg(.transparent) { $0.opacity = 0 }; $0.elements = [.time, .date, .sunrise, .sunset]
            $0.typography = TypographySpec(font: .inter, clockFont: .inter, clockWeight: .light, shadow: true)
        }),
        WidgetTemplate(id: "glass", name: "Glass", description: "Frosted glass panel with a live clock.", sizeClass: .wide, spec: spec("glass", "Glass", .clock) {
            $0.background = bg(.glassLight) { $0.opacity = 0.35 }; $0.border = BorderSpec(width: 1, opacity: 0.45)
            $0.elements = [.time, .city, .sunrise, .sunset, .icons]
            $0.typography = TypographySpec(font: .nunito, clockFont: .nunito, clockWeight: .light, shadow: true)
        }),
        WidgetTemplate(id: "dashboard", name: "Modern Dashboard", description: "Tiles for sunrise, sunset, daylight and what's next.", sizeClass: .wide, spec: spec("dashboard", "Dashboard", .dashboard) {
            $0.background = night; $0.elements = [.city, .sunrise, .sunset, .daylight, .nextEvent, .icons]
            $0.typography = TypographySpec(font: .spaceGrotesk)
        }),
        WidgetTemplate(id: "progress_circle", name: "Daylight Progress Circle", description: "How much of today's daylight has passed.", sizeClass: .square, spec: spec("progress_circle", "Progress Circle", .progressRing) {
            $0.shape = .circle; $0.background = night; $0.elements = [.progress, .sunset, .dayNight]
            $0.typography = TypographySpec(font: .nunito, weight: .bold)
        }),
        WidgetTemplate(id: "world_clock", name: "World Clock", description: "Live time in several cities with day/night dots.", sizeClass: .wide, spec: spec("world_clock", "World Clock", .multiCity) {
            var b = night; b.type = .gradient; $0.background = b; $0.elements = [.city, .time, .dayNight, .tzAbbr]
        }),
        WidgetTemplate(id: "multi_city", name: "Multi-City Sunrise & Sunset", description: "Sunrise and sunset for each of your cities.", sizeClass: .large, spec: spec("multi_city", "Multi-City", .multiCity) {
            $0.background = night; $0.elements = [.city, .sunrise, .sunset, .dayNight, .icons]
        }),
        WidgetTemplate(id: "golden_hour", name: "Golden Hour", description: "When the warm light starts and ends today.", sizeClass: .wide, spec: spec("golden_hour", "Golden Hour", .golden) {
            $0.background = bg(.gradient) { $0.color = ARGB(0xFFFFE2A6); $0.color2 = ARGB(0xFFFFA65C); $0.angle = 115 }
            $0.colors = WidgetPalettes.all[1].colors; $0.elements = [.goldenHour, .city, .sunset]
            $0.typography = TypographySpec(font: .playfair)
        }),
        WidgetTemplate(id: "compact_icon", name: "Compact Icon", description: "Icon and the next sunrise or sunset.", sizeClass: .square, spec: spec("compact_icon", "Compact", .icon) {
            $0.shape = .circle; $0.background = night; $0.elements = [.nextEvent, .icons]; $0.padding = 8
            $0.typography = TypographySpec(font: .spaceGrotesk, weight: .bold)
        }),
        WidgetTemplate(id: "large_solar", name: "Large Solar Information", description: "Everything: arc, times, twilight, golden hour.", sizeClass: .large, spec: spec("large_solar", "Large Solar", .dashboard) {
            $0.background = bg(.liveSky)
            $0.elements = [.city, .date, .time, .sunArc, .sunrise, .sunset, .daylight, .solarNoon, .goldenHour, .nextEvent, .icons, .progress]
            $0.typography = TypographySpec(font: .inter, clockFont: .spaceGrotesk)
        }),
        WidgetTemplate(id: "split", name: "Day & Night Split", description: "Warm sunrise half, cool sunset half.", sizeClass: .wide, spec: spec("split", "Split", .split) {
            $0.background = bg(.multiGradient) { $0.angle = 0; $0.colors = [ARGB(0xFFFF9A4D), ARGB(0xFFE3567A), ARGB(0xFF2B2A6B), ARGB(0xFF0B1026)] }
            $0.elements = [.sunrise, .sunset, .daylight, .icons]; $0.typography = TypographySpec(font: .oswald)
        }),
        WidgetTemplate(id: "horizon_scene", name: "Horizon Scene", description: "Mountain silhouette with the sun path above it.", sizeClass: .wide, spec: spec("horizon_scene", "Horizon", .arc) {
            $0.background = bg(.scene) { $0.scene = .mountains }; $0.elements = [.sunArc, .sunrise, .sunset, .icons]
            $0.typography = TypographySpec(font: .quicksand, weight: .bold, shadow: true)
        }),
        WidgetTemplate(id: "next_event", name: "Next Event Countdown", description: "Big countdown to the next sunrise or sunset.", sizeClass: .square, spec: spec("next_event", "Countdown", .nextEvent) {
            $0.shape = .cornerAccent; $0.cornerRadius = 28; $0.background = night; $0.elements = [.nextEvent, .city, .icons]
            $0.typography = TypographySpec(font: .bebas, clockFont: .bebas, letterSpacing: 0.04)
        }),
        WidgetTemplate(id: "digital_mono", name: "Retro Digital", description: "LCD-style clock with sun times.", sizeClass: .wide, spec: spec("digital_mono", "Retro Digital", .clock) {
            $0.shape = .square; $0.cornerRadius = 6; $0.background = bg(.solid) { $0.color = ARGB(0xFF050505) }
            $0.colors = ColorSpec(0xFFFFB547, 0xB3FFB547, 0xFFFFB547, 0xFFFF7A3D, 0xFFFFB547, 0xFFFFB547, 0xFFFF7A3D)
            $0.elements = [.time, .sunrise, .sunset, .tzAbbr]
            $0.typography = TypographySpec(font: .jetbrainsMono, clockFont: .orbitron, clockWeight: .regular)
        }),
        WidgetTemplate(id: "paper", name: "Warm Paper", description: "Serif typography on warm paper — calm and classic.", sizeClass: .square, spec: spec("paper", "Warm Paper", .stack) {
            $0.background = bg(.scene) { $0.scene = .paper }; $0.colors = WidgetPalettes.all[6].colors
            $0.elements = [.city, .date, .sunrise, .sunset, .daylight]
            $0.typography = TypographySpec(font: .cormorant, weight: .bold, labelCase: .title)
        }),
        WidgetTemplate(id: "pill_next", name: "Sun Pill", description: "A slim capsule: icon, next event and countdown.", sizeClass: .small, spec: spec("pill_next", "Sun Pill", .nextEvent) {
            $0.shape = .capsule; $0.background = bg(.gradient) { $0.color = ARGB(0xFFFF7A3D); $0.color2 = ARGB(0xFFFFB547); $0.angle = 0 }
            $0.colors = WidgetPalettes.all[0].colors; $0.elements = [.nextEvent, .icons]
            $0.typography = TypographySpec(font: .nunito, weight: .bold)
        }),
        WidgetTemplate(id: "aurora_ring", name: "Aurora Ring", description: "Glowing ring of daylight progress on a dark sky.", sizeClass: .square, spec: spec("aurora_ring", "Aurora Ring", .progressRing) {
            $0.shape = .ring; $0.background = bg(.scene) { $0.scene = .aurora }; $0.colors = WidgetPalettes.all[10].colors
            $0.elements = [.progress, .sunrise, .sunset]; $0.typography = TypographySpec(font: .quicksand, weight: .bold)
        }),
    ]

    public static func byId(_ id: String) -> WidgetTemplate? { all.first { $0.id == id } }
    public static var `default`: WidgetTemplate { all[1] }
}
