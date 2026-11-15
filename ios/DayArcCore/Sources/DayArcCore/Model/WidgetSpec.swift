import Foundation

// Widget designs share the exact JSON format of the Android app (enum names, field names,
// ARGB colors as signed 32-bit ints), so backups move between Android and iPhone.

public protocol LabeledEnum: CaseIterable, Hashable, Identifiable {
    var label: String { get }
}

public extension LabeledEnum where Self: RawRepresentable, RawValue == String {
    var id: String { rawValue }
}

public enum WidgetLayout: String, Codable, LabeledEnum {
    case stack = "STACK", clock = "CLOCK", solarDial = "SOLAR_DIAL", progressRing = "PROGRESS_RING", arc = "ARC",
         bar = "BAR", dashboard = "DASHBOARD", multiCity = "MULTI_CITY", golden = "GOLDEN", icon = "ICON",
         split = "SPLIT", nextEvent = "NEXT_EVENT"
    public var label: String {
        switch self {
        case .stack: return "Text stack"
        case .clock: return "Clock hero"
        case .solarDial: return "24-hour solar dial"
        case .progressRing: return "Progress ring"
        case .arc: return "Sun arc"
        case .bar: return "Horizontal bar"
        case .dashboard: return "Dashboard"
        case .multiCity: return "Multiple cities"
        case .golden: return "Golden hour"
        case .icon: return "Compact icon"
        case .split: return "Day / night split"
        case .nextEvent: return "Next event"
        }
    }
}

public enum WidgetShape: String, Codable, LabeledEnum {
    case square = "SQUARE", rounded = "ROUNDED", pill = "PILL", circle = "CIRCLE", oval = "OVAL", capsule = "CAPSULE",
         cornerAccent = "CORNER_ACCENT", borderless = "BORDERLESS", textOnly = "TEXT_ONLY", ring = "RING",
         horizontalBar = "HORIZONTAL_BAR", custom = "CUSTOM"
    public var label: String {
        switch self {
        case .square: return "Square"
        case .rounded: return "Rounded rectangle"
        case .pill: return "Pill"
        case .circle: return "Circle"
        case .oval: return "Oval"
        case .capsule: return "Capsule"
        case .cornerAccent: return "Corner-accent card"
        case .borderless: return "Borderless"
        case .textOnly: return "Minimal text-only"
        case .ring: return "Circular ring"
        case .horizontalBar: return "Horizontal bar"
        case .custom: return "Custom corner radius"
        }
    }
}

public enum BackgroundType: String, Codable, LabeledEnum {
    case solid = "SOLID", gradient = "GRADIENT", multiGradient = "MULTI_GRADIENT", transparent = "TRANSPARENT",
         glassDark = "GLASS_DARK", glassLight = "GLASS_LIGHT", sunrise = "SUNRISE", sunset = "SUNSET",
         nightSky = "NIGHT_SKY", scene = "SCENE", image = "IMAGE", liveSky = "LIVE_SKY"
    public var label: String {
        switch self {
        case .solid: return "Solid color"
        case .gradient: return "Two-color gradient"
        case .multiGradient: return "Multicolor gradient"
        case .transparent: return "Transparent"
        case .glassDark: return "Dark glass"
        case .glassLight: return "Light glass"
        case .sunrise: return "Sunrise gradient"
        case .sunset: return "Sunset gradient"
        case .nightSky: return "Night sky"
        case .scene: return "Built-in scene"
        case .image: return "Photo"
        case .liveSky: return "Live sky (follows the Sun)"
        }
    }
}

public enum SceneId: String, Codable, LabeledEnum {
    case horizon = "HORIZON", mountains = "MOUNTAINS", dunes = "DUNES", ocean = "OCEAN", starfield = "STARFIELD",
         aurora = "AURORA", city = "CITY", paper = "PAPER"
    public var label: String {
        switch self {
        case .horizon: return "Horizon"
        case .mountains: return "Mountains"
        case .dunes: return "Desert dunes"
        case .ocean: return "Ocean"
        case .starfield: return "Starfield"
        case .aurora: return "Aurora"
        case .city: return "City skyline"
        case .paper: return "Warm paper"
        }
    }
}

public enum WidgetFont: String, Codable, LabeledEnum {
    case inter = "INTER", nunito = "NUNITO", poppins = "POPPINS", spaceGrotesk = "SPACE_GROTESK", playfair = "PLAYFAIR",
         cormorant = "CORMORANT", jetbrainsMono = "JETBRAINS_MONO", orbitron = "ORBITRON", oswald = "OSWALD",
         bebas = "BEBAS", quicksand = "QUICKSAND"
    public var label: String {
        switch self {
        case .inter: return "Modern Sans"
        case .nunito: return "Rounded"
        case .poppins: return "Geometric"
        case .spaceGrotesk: return "Grotesk"
        case .playfair: return "Elegant Serif"
        case .cormorant: return "Classic Serif"
        case .jetbrainsMono: return "Monospace"
        case .orbitron: return "Digital"
        case .oswald: return "Condensed"
        case .bebas: return "Bold Display"
        case .quicksand: return "Soft Rounded"
        }
    }
}

public enum FontWeightChoice: String, Codable, LabeledEnum {
    case light = "LIGHT", regular = "REGULAR", bold = "BOLD"
    public var label: String { self == .light ? "Light" : self == .regular ? "Regular" : "Bold" }
}

public enum TextCase: String, Codable, LabeledEnum {
    case asIs = "AS_IS", upper = "UPPER", lower = "LOWER", title = "TITLE"
    public var label: String {
        switch self { case .asIs: return "As written"; case .upper: return "UPPERCASE"; case .lower: return "lowercase"; case .title: return "Title Case" }
    }
}

public enum WidgetAlign: String, Codable, LabeledEnum {
    case start = "START", center = "CENTER", end = "END"
    public var label: String { self == .start ? "Left" : self == .center ? "Center" : "Right" }
}

public enum WidgetElement: String, Codable, LabeledEnum {
    case city = "CITY", country = "COUNTRY", time = "TIME", date = "DATE", sunrise = "SUNRISE", sunset = "SUNSET",
         daylight = "DAYLIGHT", countdownSunrise = "COUNTDOWN_SUNRISE", countdownSunset = "COUNTDOWN_SUNSET",
         nextEvent = "NEXT_EVENT", goldenHour = "GOLDEN_HOUR", solarNoon = "SOLAR_NOON", progress = "PROGRESS",
         sunArc = "SUN_ARC", dayNight = "DAY_NIGHT", tzAbbr = "TZ_ABBR", icons = "ICONS"
    public var label: String {
        switch self {
        case .city: return "City name"
        case .country: return "Country"
        case .time: return "Current time"
        case .date: return "Current date"
        case .sunrise: return "Sunrise time"
        case .sunset: return "Sunset time"
        case .daylight: return "Daylight duration"
        case .countdownSunrise: return "Countdown to sunrise"
        case .countdownSunset: return "Countdown to sunset"
        case .nextEvent: return "Next solar event"
        case .goldenHour: return "Golden hour"
        case .solarNoon: return "Solar noon"
        case .progress: return "Daylight progress"
        case .sunArc: return "Sun arc"
        case .dayNight: return "Day/night indicator"
        case .tzAbbr: return "Time-zone abbreviation"
        case .icons: return "Sunrise/sunset icons"
        }
    }
}

/// ARGB color stored like Android (signed 32-bit int).
public struct ARGB: Codable, Hashable {
    public var value: Int32
    public init(_ v: UInt32) { value = Int32(bitPattern: v) }
    public init(int: Int32) { value = int }
    public init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if let i = try? c.decode(Int64.self) { value = Int32(truncatingIfNeeded: i) } else { value = Int32(bitPattern: 0xFFFFFFFF) }
    }
    public func encode(to encoder: Encoder) throws { var c = encoder.singleValueContainer(); try c.encode(value) }
    public var u: UInt32 { UInt32(bitPattern: value) }
    public var a: Double { Double((u >> 24) & 0xFF) / 255 }
    public var r: Double { Double((u >> 16) & 0xFF) / 255 }
    public var g: Double { Double((u >> 8) & 0xFF) / 255 }
    public var b: Double { Double(u & 0xFF) / 255 }
    public init(r: Double, g: Double, b: Double, a: Double = 1) {
        func c(_ x: Double) -> UInt32 { UInt32(max(0, min(255, (x * 255).rounded()))) }
        self.init((c(a) << 24) | (c(r) << 16) | (c(g) << 8) | c(b))
    }
    public var hex: String { a >= 0.999 ? String(format: "#%06X", u & 0xFFFFFF) : String(format: "#%08X", u) }
    public static func parse(hex: String) -> ARGB? {
        var t = hex.trimmingCharacters(in: .whitespaces)
        if t.hasPrefix("#") { t.removeFirst() }
        guard let v = UInt32(t, radix: 16) else { return nil }
        switch t.count { case 6: return ARGB(0xFF000000 | v); case 8: return ARGB(v); default: return nil }
    }
}

private extension KeyedDecodingContainer {
    func v<T: Decodable>(_ key: Key, _ def: T) -> T { (try? decodeIfPresent(T.self, forKey: key)) ?? def }
}

public struct BackgroundSpec: Codable, Hashable {
    public var type: BackgroundType = .solid
    public var color = ARGB(0xFF101733)
    public var color2 = ARGB(0xFF2A1B4D)
    public var colors: [ARGB] = [ARGB(0xFF1B2A6B), ARGB(0xFF8E3B8A), ARGB(0xFFFF8A3D)]
    public var angle: Double = 90
    public var opacity: Double = 1
    public var scene: SceneId = .horizon
    public var imagePath: String? = nil
    public var imageZoom: Double = 1
    public var imageOffsetX: Double = 0
    public var imageOffsetY: Double = 0
    public var imageBrightness: Double = 0
    public var imageOpacity: Double = 1
    public var overlayColor = ARGB(0xFF000000)
    public var overlayOpacity: Double = 0.25

    public init() {}
    enum CodingKeys: String, CodingKey { case type, color, color2, colors, angle, opacity, scene, imagePath, imageZoom, imageOffsetX, imageOffsetY, imageBrightness, imageOpacity, overlayColor, overlayOpacity }
    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self); let x = BackgroundSpec.zero
        type = c.v(.type, x.type); color = c.v(.color, x.color); color2 = c.v(.color2, x.color2); colors = c.v(.colors, x.colors)
        angle = c.v(.angle, x.angle); opacity = c.v(.opacity, x.opacity); scene = c.v(.scene, x.scene)
        imagePath = (try? c.decodeIfPresent(String.self, forKey: .imagePath)) ?? nil
        imageZoom = c.v(.imageZoom, x.imageZoom); imageOffsetX = c.v(.imageOffsetX, x.imageOffsetX); imageOffsetY = c.v(.imageOffsetY, x.imageOffsetY)
        imageBrightness = c.v(.imageBrightness, x.imageBrightness); imageOpacity = c.v(.imageOpacity, x.imageOpacity)
        overlayColor = c.v(.overlayColor, x.overlayColor); overlayOpacity = c.v(.overlayOpacity, x.overlayOpacity)
    }
    static let zero = BackgroundSpec()
}

public struct BorderSpec: Codable, Hashable {
    public var width: Double = 0
    public var color = ARGB(0xFFFFFFFF)
    public var opacity: Double = 0.25
    public init(width: Double = 0, color: ARGB = ARGB(0xFFFFFFFF), opacity: Double = 0.25) { self.width = width; self.color = color; self.opacity = opacity }
    enum CodingKeys: String, CodingKey { case width, color, opacity }
    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        width = c.v(.width, 0); color = c.v(.color, ARGB(0xFFFFFFFF)); opacity = c.v(.opacity, 0.25)
    }
}

public struct ColorSpec: Codable, Hashable {
    public var text = ARGB(0xFFFFFFFF)
    public var secondaryText = ARGB(0xCCFFFFFF)
    public var sunrise = ARGB(0xFFFFB547)
    public var sunset = ARGB(0xFFFF7A3D)
    public var arc = ARGB(0xFFFFC870)
    public var icon = ARGB(0xFFFFC14D)
    public var accent = ARGB(0xFF7FB2FF)
    public init() {}
    public init(_ t: UInt32, _ s: UInt32, _ sr: UInt32, _ ss: UInt32, _ a: UInt32, _ i: UInt32, _ ac: UInt32) {
        text = ARGB(t); secondaryText = ARGB(s); sunrise = ARGB(sr); sunset = ARGB(ss); arc = ARGB(a); icon = ARGB(i); accent = ARGB(ac)
    }
    enum CodingKeys: String, CodingKey { case text, secondaryText, sunrise, sunset, arc, icon, accent }
    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self); let x = ColorSpec()
        text = c.v(.text, x.text); secondaryText = c.v(.secondaryText, x.secondaryText); sunrise = c.v(.sunrise, x.sunrise)
        sunset = c.v(.sunset, x.sunset); arc = c.v(.arc, x.arc); icon = c.v(.icon, x.icon); accent = c.v(.accent, x.accent)
    }
}

public struct TypographySpec: Codable, Hashable {
    public var font: WidgetFont = .inter
    public var weight: FontWeightChoice = .regular
    public var clockFont: WidgetFont? = nil
    public var clockWeight: FontWeightChoice = .light
    public var scale: Double = 1
    public var clockScale: Double = 1
    public var labelScale: Double = 1
    public var letterSpacing: Double = 0
    public var `case`: TextCase = .asIs
    public var labelCase: TextCase = .upper
    public var shadow: Bool = false
    public init(font: WidgetFont = .inter, weight: FontWeightChoice = .regular, clockFont: WidgetFont? = nil, clockWeight: FontWeightChoice = .light,
                letterSpacing: Double = 0, labelCase: TextCase = .upper, shadow: Bool = false) {
        self.font = font; self.weight = weight; self.clockFont = clockFont; self.clockWeight = clockWeight
        self.letterSpacing = letterSpacing; self.labelCase = labelCase; self.shadow = shadow
    }
    enum CodingKeys: String, CodingKey { case font, weight, clockFont, clockWeight, scale, clockScale, labelScale, letterSpacing, `case`, labelCase, shadow }
    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        font = c.v(.font, .inter); weight = c.v(.weight, .regular)
        clockFont = (try? c.decodeIfPresent(WidgetFont.self, forKey: .clockFont)) ?? nil
        clockWeight = c.v(.clockWeight, .light); scale = c.v(.scale, 1); clockScale = c.v(.clockScale, 1); labelScale = c.v(.labelScale, 1)
        letterSpacing = c.v(.letterSpacing, 0); `case` = c.v(.case, .asIs); labelCase = c.v(.labelCase, .upper); shadow = c.v(.shadow, false)
    }
}

public struct WidgetSpec: Codable, Hashable {
    public var version: Int = 1
    public var templateId: String
    public var name: String
    public var layout: WidgetLayout
    public var shape: WidgetShape = .rounded
    public var cornerRadius: Double = 26
    public var background = BackgroundSpec()
    public var border = BorderSpec()
    public var colors = ColorSpec()
    public var typography = TypographySpec()
    public var elements: Set<WidgetElement> = [.sunrise, .sunset]
    public var align: WidgetAlign = .center
    public var padding: Double = 14
    public var spacing: Double = 1
    public var decorOpacity: Double = 1
    public var textOpacity: Double = 1
    /// Location id (iOS uses UUID strings; Android ids are dropped on import). Nil = default location.
    public var locationId: String? = nil
    public var extraLocationIds: [String] = []
    public var liveClock: Bool = true
    public var liveCountdown: Bool = false

    public init(templateId: String, name: String, layout: WidgetLayout) {
        self.templateId = templateId; self.name = name; self.layout = layout
    }

    public func has(_ e: WidgetElement) -> Bool { elements.contains(e) }

    enum CodingKeys: String, CodingKey {
        case version, templateId, name, layout, shape, cornerRadius, background, border, colors, typography, elements, align,
             padding, spacing, decorOpacity, textOpacity, locationId, extraLocationIds, liveClock, liveCountdown
    }

    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        templateId = c.v(.templateId, "custom"); name = c.v(.name, "Widget"); layout = c.v(.layout, .stack)
        version = c.v(.version, 1); shape = c.v(.shape, .rounded); cornerRadius = c.v(.cornerRadius, 26)
        background = c.v(.background, BackgroundSpec()); border = c.v(.border, BorderSpec()); colors = c.v(.colors, ColorSpec())
        typography = c.v(.typography, TypographySpec())
        let els: [String] = c.v(.elements, ["SUNRISE", "SUNSET"]); elements = Set(els.compactMap { WidgetElement(rawValue: $0) })
        align = c.v(.align, .center); padding = c.v(.padding, 14); spacing = c.v(.spacing, 1)
        decorOpacity = c.v(.decorOpacity, 1); textOpacity = c.v(.textOpacity, 1)
        locationId = (try? c.decodeIfPresent(String.self, forKey: .locationId)) ?? nil
        extraLocationIds = c.v(.extraLocationIds, [])
        liveClock = c.v(.liveClock, true); liveCountdown = c.v(.liveCountdown, false)
    }

    public func encode(to e: Encoder) throws {
        var c = e.container(keyedBy: CodingKeys.self)
        try c.encode(version, forKey: .version); try c.encode(templateId, forKey: .templateId); try c.encode(name, forKey: .name)
        try c.encode(layout, forKey: .layout); try c.encode(shape, forKey: .shape); try c.encode(cornerRadius, forKey: .cornerRadius)
        try c.encode(background, forKey: .background); try c.encode(border, forKey: .border); try c.encode(colors, forKey: .colors)
        try c.encode(typography, forKey: .typography)
        try c.encode(WidgetElement.allCases.filter { elements.contains($0) }, forKey: .elements)
        try c.encode(align, forKey: .align); try c.encode(padding, forKey: .padding); try c.encode(spacing, forKey: .spacing)
        try c.encode(decorOpacity, forKey: .decorOpacity); try c.encode(textOpacity, forKey: .textOpacity)
        try c.encodeIfPresent(locationId, forKey: .locationId); try c.encode(extraLocationIds, forKey: .extraLocationIds)
        try c.encode(liveClock, forKey: .liveClock); try c.encode(liveCountdown, forKey: .liveCountdown)
    }
}
