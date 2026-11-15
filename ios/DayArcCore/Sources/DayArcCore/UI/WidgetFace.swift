import SwiftUI
#if canImport(UIKit)
import UIKit
import ImageIO
#endif

public struct FaceCity {
    public let title: String
    public let country: String
    public let zone: TimeZone
    public let snap: SunSnapshot
    public init(_ l: SavedLocation, now: Date) {
        title = l.displayName; country = l.countryName; zone = l.zone; snap = SolarService.shared.snapshot(l, at: now)
    }
}

public struct FaceData {
    public let primary: FaceCity?
    public let others: [FaceCity]
    public let now: Date
    public let use24h: Bool
    public let dateStyle: DateStyle
    /// In a real widget, countdowns can use the system's ticking timer text.
    public let isWidget: Bool

    public init(primary: FaceCity?, others: [FaceCity] = [], now: Date, use24h: Bool, dateStyle: DateStyle = .medium, isWidget: Bool = false) {
        self.primary = primary; self.others = others; self.now = now; self.use24h = use24h; self.dateStyle = dateStyle; self.isWidget = isWidget
    }

    /// Builds face data for a spec from the shared store.
    public static func load(for spec: WidgetSpec, now: Date = Date(), isWidget: Bool = false, overrideLocation: SavedLocation? = nil) -> FaceData {
        let all = SharedStore.loadLocations()
        let settings = SharedStore.loadSettings()
        let primary = overrideLocation ?? SharedStore.resolveLocation(spec.locationId, in: all)
        let others: [SavedLocation]
        if !spec.extraLocationIds.isEmpty {
            others = spec.extraLocationIds.compactMap { id in all.first { $0.id == id } }
        } else {
            others = all.filter { $0.id != primary?.id && $0.inCityList }
                .sorted { ($0.isFavorite ? 0 : 1, $0.sortOrder) < ($1.isFavorite ? 0 : 1, $1.sortOrder) }
                .prefix(5).map { $0 }
        }
        return FaceData(primary: primary.map { FaceCity($0, now: now) }, others: others.filter { $0.id != primary?.id }.map { FaceCity($0, now: now) },
                        now: now, use24h: settings.use24h, dateStyle: settings.dateStyle, isWidget: isWidget)
    }
}

// MARK: - Shape

struct FaceShape: Shape {
    let spec: WidgetSpec
    func path(in rect: CGRect) -> Path {
        let m = min(rect.width, rect.height)
        switch spec.shape {
        case .circle, .ring:
            return Path(ellipseIn: CGRect(x: rect.midX - m / 2, y: rect.midY - m / 2, width: m, height: m))
        case .oval: return Path(ellipseIn: rect)
        case .pill, .capsule: return Path(roundedRect: rect, cornerRadius: m / 2, style: .continuous)
        case .horizontalBar:
            let bh = min(rect.height, max(min(rect.width * 0.3, 96), 44))
            let b = CGRect(x: rect.minX, y: rect.midY - bh / 2, width: rect.width, height: bh)
            return Path(roundedRect: b, cornerRadius: bh / 2, style: .continuous)
        case .square: return Path(rect)
        case .textOnly: return Path()
        default: return Path(roundedRect: rect, cornerRadius: min(spec.cornerRadius, m / 2), style: .continuous)
        }
    }
}

/// Content area inside the shape, like the Android renderer.
func contentInsets(_ spec: WidgetSpec, _ size: CGSize) -> EdgeInsets {
    let m = min(size.width, size.height)
    let pad = min(spec.padding, m * 0.1)
    switch spec.shape {
    case .circle, .ring:
        let inner = m * 0.70
        let h = (size.width - inner) / 2, v = (size.height - inner) / 2
        return EdgeInsets(top: v, leading: h, bottom: v, trailing: h)
    case .oval: return EdgeInsets(top: size.height * 0.15, leading: size.width * 0.15, bottom: size.height * 0.15, trailing: size.width * 0.15)
    case .pill, .capsule:
        let r = m / 2
        if size.width >= size.height { return EdgeInsets(top: pad * 0.55, leading: r * 0.45 + pad * 0.3, bottom: pad * 0.55, trailing: r * 0.45 + pad * 0.3) }
        return EdgeInsets(top: pad, leading: pad, bottom: pad, trailing: pad)
    case .horizontalBar:
        let bh = min(size.height, max(min(size.width * 0.3, 96), 44))
        let v = (size.height - bh) / 2 + pad * 0.5
        return EdgeInsets(top: v, leading: bh * 0.3, bottom: v, trailing: bh * 0.3)
    case .textOnly: return EdgeInsets(top: pad * 0.5, leading: pad * 0.5, bottom: pad * 0.5, trailing: pad * 0.5)
    default:
        let extra = min(spec.cornerRadius, m / 2) * 0.12
        return EdgeInsets(top: pad, leading: pad + extra, bottom: pad, trailing: pad + extra)
    }
}

// MARK: - Background

struct FaceBackground: View {
    let spec: WidgetSpec
    let elevation: Double

    var body: some View {
        let bg = spec.background
        ZStack {
            switch bg.type {
            case .solid: bg.color.color
            case .gradient: LinearGradient(colors: [bg.color.color, bg.color2.color], startPoint: start(bg.angle), endPoint: end(bg.angle))
            case .multiGradient:
                let cs = bg.colors.isEmpty ? [bg.color, bg.color2] : bg.colors
                LinearGradient(colors: (cs.count == 1 ? cs + cs : cs).map { $0.color }, startPoint: start(bg.angle), endPoint: end(bg.angle))
            case .transparent: Color.clear
            case .glassDark: Glass(dark: true)
            case .glassLight: Glass(dark: false)
            case .sunrise:
                ZStack {
                    LinearGradient(colors: [Color(hex: 0xFF1B2A6B), Color(hex: 0xFF6B4A9E), Color(hex: 0xFFFF8A5C), Color(hex: 0xFFFFC56B)], startPoint: .top, endPoint: .bottom)
                    RadialGradient(colors: [Color(hex: 0x66FFE0A0).opacity(spec.decorOpacity), .clear], center: .bottom, startRadius: 0, endRadius: 220)
                }
            case .sunset:
                ZStack {
                    LinearGradient(colors: [Color(hex: 0xFF2B1B4D), Color(hex: 0xFF8E3B8A), Color(hex: 0xFFE0566A), Color(hex: 0xFFFF9A4D)], startPoint: .top, endPoint: .bottom)
                    RadialGradient(colors: [Color(hex: 0x55FFB070).opacity(spec.decorOpacity), .clear], center: .bottom, startRadius: 0, endRadius: 200)
                }
            case .nightSky:
                ZStack {
                    LinearGradient(colors: [Color(hex: 0xFF03050F), Color(hex: 0xFF0B1026), Color(hex: 0xFF1B2A6B)], startPoint: .top, endPoint: .bottom)
                    Stars(seed: 7, count: 70).fill(Color.white.opacity(0.75 * spec.decorOpacity))
                }
            case .scene: SceneView(scene: bg.scene, decor: spec.decorOpacity)
            case .image: PhotoBackground(bg: bg)
            case .liveSky: LiveSky(elevation: elevation, decor: spec.decorOpacity)
            }
        }
    }

    func start(_ a: Double) -> UnitPoint { let r = a * .pi / 180; return UnitPoint(x: 0.5 - cos(r) / 2, y: 0.5 - sin(r) / 2) }
    func end(_ a: Double) -> UnitPoint { let r = a * .pi / 180; return UnitPoint(x: 0.5 + cos(r) / 2, y: 0.5 + sin(r) / 2) }
}

/// Widgets cannot blur the wallpaper; glass is approximated with a translucent tint, sheen and a bright edge.
struct Glass: View {
    let dark: Bool
    var body: some View {
        ZStack(alignment: .top) {
            LinearGradient(colors: dark ? [Color(hex: 0xCC1A2140), Color(hex: 0xE00B1026)] : [Color(hex: 0x99FFFFFF), Color(hex: 0x55FFFFFF)], startPoint: .top, endPoint: .bottom)
            LinearGradient(colors: [Color.white.opacity(dark ? 0.15 : 0.3), .clear], startPoint: .topLeading, endPoint: .center)
            Rectangle().fill(Color.white.opacity(dark ? 0.25 : 0.6)).frame(height: 1)
        }
    }
}

struct Stars: Shape {
    let seed: UInt64
    let count: Int
    func path(in rect: CGRect) -> Path {
        var rng = SplitMix(seed: seed)
        var p = Path()
        for _ in 0..<count {
            let x = rect.minX + rng.next() * rect.width, y = rect.minY + rng.next() * rect.height * 0.85
            let r = 0.4 + rng.next() * 0.9
            p.addEllipse(in: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
        }
        return p
    }
}

struct SplitMix {
    var state: UInt64
    init(seed: UInt64) { state = seed &+ 0x9E3779B97F4A7C15 }
    mutating func next() -> Double {
        state &+= 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        z = z ^ (z >> 31)
        return Double(z >> 11) / Double(1 << 53)
    }
}

struct Ridge: Shape {
    let base: Double, amp: Double, freq: Double, phase: Double
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: rect.minX, y: rect.maxY))
        let steps = 80
        for i in 0...steps {
            let t = Double(i) / Double(steps)
            let y = rect.minY + rect.height * base - rect.height * amp * (0.55 * sin(t * freq + phase) + 0.3 * sin(t * freq * 2.3 + phase * 1.7) + 0.15 * sin(t * freq * 5.1))
            p.addLine(to: CGPoint(x: rect.minX + t * rect.width, y: y))
        }
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY)); p.closeSubpath()
        return p
    }
}

struct SceneView: View {
    let scene: SceneId
    let decor: Double
    func sky(_ c: [UInt32]) -> some View { LinearGradient(colors: c.map { Color(hex: $0) }, startPoint: .top, endPoint: .bottom) }
    var body: some View {
        switch scene {
        case .horizon:
            ZStack(alignment: .bottom) {
                sky([0xFF0E1A4A, 0xFF5B3F8C, 0xFFFF8A5C, 0xFFFFC56B])
                RadialGradient(colors: [Color(hex: 0x99FFD08A).opacity(decor), .clear], center: UnitPoint(x: 0.5, y: 0.78), startRadius: 0, endRadius: 160)
                GeometryReader { g in Rectangle().fill(Color(hex: 0xFF1A1030)).frame(height: g.size.height * 0.2).frame(maxHeight: .infinity, alignment: .bottom) }
            }
        case .mountains:
            ZStack {
                sky([0xFF1B2450, 0xFF6A4A8F, 0xFFF08A6A, 0xFFFFC27A])
                RadialGradient(colors: [Color(hex: 0x88FFD9A0).opacity(decor), .clear], center: UnitPoint(x: 0.7, y: 0.62), startRadius: 0, endRadius: 120)
                Ridge(base: 0.70, amp: 0.16, freq: 7, phase: 0.4).fill(Color(hex: 0xFF4A3466))
                Ridge(base: 0.80, amp: 0.13, freq: 9, phase: 2.1).fill(Color(hex: 0xFF2D2045))
                Ridge(base: 0.90, amp: 0.08, freq: 12, phase: 4.0).fill(Color(hex: 0xFF181127))
            }
        case .dunes:
            ZStack {
                sky([0xFFF6D3A0, 0xFFF2B07A, 0xFFE08A5A])
                Ridge(base: 0.72, amp: 0.08, freq: 4, phase: 1).fill(Color(hex: 0xFFD9935A))
                Ridge(base: 0.84, amp: 0.07, freq: 5, phase: 3).fill(Color(hex: 0xFFB86B3E))
                Ridge(base: 0.94, amp: 0.05, freq: 6, phase: 5).fill(Color(hex: 0xFF8C4B2B))
            }
        case .ocean:
            ZStack {
                sky([0xFF0A2A5C, 0xFF1B5F9E, 0xFF7FC3E8])
                Ridge(base: 0.66, amp: 0.005, freq: 30, phase: 0).fill(LinearGradient(colors: [Color(hex: 0xFF0E4D7A), Color(hex: 0xFF06243F)], startPoint: .top, endPoint: .bottom))
            }
        case .starfield:
            ZStack {
                sky([0xFF02030A, 0xFF070B1F, 0xFF101A3D])
                Stars(seed: 3, count: 110).fill(Color.white.opacity(0.85 * decor))
            }
        case .aurora:
            ZStack {
                sky([0xFF020814, 0xFF06202E, 0xFF0B3B3A])
                Stars(seed: 5, count: 60).fill(Color.white.opacity(0.6 * decor))
                Ridge(base: 0.40, amp: 0.10, freq: 4, phase: 0.5).stroke(Color(hex: 0xFF6EF2C2).opacity(0.5 * decor), lineWidth: 18).blur(radius: 14)
                Ridge(base: 0.50, amp: 0.10, freq: 5, phase: 1.5).stroke(Color(hex: 0xFFB28CFF).opacity(0.35 * decor), lineWidth: 14).blur(radius: 12)
            }
        case .city:
            ZStack {
                sky([0xFF0B1026, 0xFF3B2A6B, 0xFFE0566A])
                Skyline().fill(Color(hex: 0xFF0A0A14))
            }
        case .paper:
            ZStack {
                sky([0xFFFBF4EA, 0xFFF3E6D3])
                Stars(seed: 13, count: 120).fill(Color.black.opacity(0.06))
            }
        }
    }
}

struct Skyline: Shape {
    func path(in rect: CGRect) -> Path {
        var rng = SplitMix(seed: 21)
        var p = Path()
        var x = rect.minX
        while x < rect.maxX {
            let w = rect.width * (0.04 + rng.next() * 0.07), h = rect.height * (0.15 + rng.next() * 0.35)
            p.addRect(CGRect(x: x, y: rect.maxY - h, width: w, height: h))
            x += w + 1
        }
        return p
    }
}

struct LiveSky: View {
    let elevation: Double
    let decor: Double
    var body: some View {
        let c: [UInt32]
        if elevation >= 12 { c = [0xFF2F6FD6, 0xFF5A9BEA, 0xFFA9D2F7] }
        else if elevation >= 4 { c = [0xFF3A5DB5, 0xFFE8A06B, 0xFFFFD08A] }
        else if elevation >= -1 { c = [0xFF33307A, 0xFFD9667A, 0xFFFFA65C] }
        else if elevation >= -6 { c = [0xFF1A1E52, 0xFF5A3F8C, 0xFFC4657A] }
        else if elevation >= -12 { c = [0xFF0B1033, 0xFF1E2A66, 0xFF3B3F86] }
        else { c = [0xFF03050F, 0xFF0B1026, 0xFF16204A] }
        return ZStack {
            LinearGradient(colors: c.map { Color(hex: $0) }, startPoint: .top, endPoint: .bottom)
            if elevation < -4 {
                Stars(seed: 11, count: 80).fill(Color.white.opacity(min(1, max(0, (-4 - elevation) / 10)) * decor))
            }
        }
    }
}

struct PhotoBackground: View {
    let bg: BackgroundSpec
    var body: some View {
        GeometryReader { g in
            #if canImport(UIKit)
            if let path = bg.imagePath, let img = PhotoCache.image(path, maxPixel: max(g.size.width, g.size.height) * 3) {
                let zoom = max(1, min(4, bg.imageZoom))
                let scale = max(g.size.width / img.size.width, g.size.height / img.size.height) * zoom
                let dw = img.size.width * scale, dh = img.size.height * scale
                let ex = (dw - g.size.width) / 2, ey = (dh - g.size.height) / 2
                ZStack {
                    Image(uiImage: img).resizable().frame(width: dw, height: dh)
                        .offset(x: -bg.imageOffsetX * ex, y: -bg.imageOffsetY * ey)
                        .brightness(bg.imageBrightness * 0.35)
                        .opacity(bg.imageOpacity)
                        .frame(width: g.size.width, height: g.size.height)
                        .clipped()
                    bg.overlayColor.opacity(bg.overlayOpacity)
                }
            } else {
                LinearGradient(colors: [bg.color.color, bg.color2.color], startPoint: .top, endPoint: .bottom)
            }
            #else
            LinearGradient(colors: [bg.color.color, bg.color2.color], startPoint: .top, endPoint: .bottom)
            #endif
        }
    }
}

#if canImport(UIKit)
/// Downsampled photo loading (widgets have tight memory limits).
public enum PhotoCache {
    static let cache = NSCache<NSString, UIImage>()
    public static func resolve(_ path: String) -> URL {
        // Paths are stored relative to the shared images folder so they survive container moves.
        path.hasPrefix("/") ? URL(fileURLWithPath: path) : SharedStore.imagesDir.appendingPathComponent(path)
    }
    public static func image(_ path: String, maxPixel: CGFloat) -> UIImage? {
        let key = "\(path)@\(Int(maxPixel))" as NSString
        if let i = cache.object(forKey: key) { return i }
        let url = resolve(path)
        guard let src = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        let opts: [CFString: Any] = [kCGImageSourceCreateThumbnailFromImageAlways: true, kCGImageSourceThumbnailMaxPixelSize: max(64, maxPixel),
                                     kCGImageSourceCreateThumbnailWithTransform: true]
        guard let cg = CGImageSourceCreateThumbnailAtIndex(src, 0, opts as CFDictionary) else { return nil }
        let img = UIImage(cgImage: cg)
        cache.setObject(img, forKey: key)
        return img
    }
}
#endif

// MARK: - Face

/// One widget face. The same view renders the home-screen widget and the Widget Studio preview.
public struct WidgetFace: View {
    let spec: WidgetSpec
    let data: FaceData

    public init(spec: WidgetSpec, data: FaceData) { self.spec = spec; self.data = data; FontRegistry.registerAll() }

    public var body: some View {
        GeometryReader { g in
            let size = g.size
            let shape = FaceShape(spec: spec)
            ZStack {
                if spec.background.type != .transparent && spec.background.opacity > 0.001 && spec.shape != .textOnly {
                    FaceBackground(spec: spec, elevation: data.primary?.snap.position.elevation ?? -30)
                        .opacity(spec.background.opacity)
                        .clipShape(shape)
                        .overlay(
                            spec.shape == .ring
                                ? AnyView(Circle().fill(Color.black).frame(width: min(size.width, size.height) * 0.66).blendMode(.destinationOut))
                                : AnyView(EmptyView())
                        )
                        .compositingGroup()
                }
                if spec.shape == .cornerAccent {
                    RadialGradient(colors: [spec.colors.accent.opacity(0.85 * spec.decorOpacity), .clear], center: .topTrailing, startRadius: 0,
                                   endRadius: min(size.width, size.height) * 0.55)
                        .clipShape(shape)
                }
                if spec.border.width > 0 && spec.border.opacity > 0 && spec.shape != .borderless && spec.shape != .textOnly {
                    shape.stroke(spec.border.color.opacity(spec.border.opacity), lineWidth: spec.border.width)
                }
                if let city = data.primary {
                    FaceContent(spec: spec, data: data, city: city, size: size)
                        .padding(contentInsets(spec, size))
                    if spec.has(.dayNight) && ![.multiCity, .solarDial, .progressRing].contains(spec.layout) {
                        GlyphView(city.snap.isDay ? .sun : .moon, color: spec.colors.icon.opacity(spec.textOpacity))
                            .frame(width: 14, height: 14)
                            .position(x: size.width - max(12, contentInsets(spec, size).trailing), y: max(12, contentInsets(spec, size).top))
                    }
                } else {
                    Text("Open DayArc to choose a location").font(.system(size: 12)).foregroundColor(spec.colors.secondaryText.color)
                        .multilineTextAlignment(.center).padding()
                }
            }
            .frame(width: size.width, height: size.height)
        }
    }
}

// MARK: - Text helpers

struct FaceStyle {
    let spec: WidgetSpec
    var t: TypographySpec { spec.typography }
    var col: ColorSpec { spec.colors }

    enum Role { case clock, title, value, label }

    func cased(_ s: String, _ role: Role) -> String {
        switch role == .label ? t.labelCase : t.case {
        case .asIs: return s
        case .upper: return s.split(separator: " ").map { w in w.contains(where: { $0.isNumber }) ? String(w) : w.uppercased() }.joined(separator: " ")
        case .lower: return s.lowercased()
        case .title: return s.capitalized
        }
    }

    func font(_ role: Role, _ size: CGFloat) -> Font {
        let (f, w): (WidgetFont, FontWeightChoice)
        switch role {
        case .clock: (f, w) = (t.clockFont ?? t.font, t.clockWeight)
        case .title: (f, w) = (t.font, t.weight == .light ? .regular : .bold)
        case .label: (f, w) = (t.font, t.weight == .bold ? .regular : t.weight)
        case .value: (f, w) = (t.font, t.weight)
        }
        let scale = role == .clock ? t.scale * t.clockScale : role == .label ? t.scale * t.labelScale : t.scale
        return FontRegistry.font(f, w, size: max(4, size * scale))
    }

    func text(_ s: String, _ role: Role, _ size: CGFloat, _ color: ARGB, align: WidgetAlign = .center) -> some View {
        let tracking = size * (t.letterSpacing + (role == .label ? 0.06 : 0))
        return Text(cased(s, role))
            .font(font(role, size))
            .tracking(tracking)
            .foregroundColor(color.opacity(spec.textOpacity))
            .lineLimit(1)
            .minimumScaleFactor(0.2)
            .shadow(color: t.shadow ? Color.black.opacity(0.55) : .clear, radius: t.shadow ? 3 : 0, x: 0, y: 1)
            .frame(maxWidth: .infinity, alignment: align.frameAlignment)
    }

    func glyph(_ gl: Glyph, _ color: ARGB, _ s: CGFloat) -> some View {
        GlyphView(gl, color: color.opacity(spec.textOpacity)).frame(width: s, height: s)
    }
}

extension WidgetAlign {
    var frameAlignment: Alignment { self == .start ? .leading : self == .center ? .center : .trailing }
    var hAlignment: HorizontalAlignment { self == .start ? .leading : self == .center ? .center : .trailing }
}
