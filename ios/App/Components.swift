import SwiftUI
import DayArcCore

/// App design tokens (match the Android app): navy / warm white, golden amber primary.
enum DS {
    static let corner: CGFloat = 20
    static let screen: CGFloat = 18
    static func card(_ scheme: ColorScheme) -> Color { scheme == .dark ? Palette.navy850 : Palette.sand50 }
    static func bg(_ scheme: ColorScheme) -> Color { scheme == .dark ? Palette.navy900 : Palette.warmWhite }
    static func sunrise(_ s: ColorScheme) -> Color { s == .dark ? Palette.amber : Color(hex: 0xFFE08A00) }
    static func sunset(_ s: ColorScheme) -> Color { s == .dark ? Palette.sunset : Color(hex: 0xFFC94F1C) }
    static func blue(_ s: ColorScheme) -> Color { s == .dark ? Palette.softBlue : Color(hex: 0xFF3D6FD1) }
    static func display(_ size: CGFloat, _ w: FontWeightChoice = .regular) -> Font { FontRegistry.font(.spaceGrotesk, w, size: size) }
}

struct Card<Content: View>: View {
    @Environment(\.colorScheme) var scheme
    var padding: CGFloat = 16
    @ViewBuilder var content: Content
    var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: DS.corner, style: .continuous).fill(DS.card(scheme)))
    }
}

struct SectionTitle: View {
    let title: String
    var body: some View {
        Text(title.uppercased())
            .font(.caption.weight(.semibold))
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 16).padding(.bottom, 4)
            .accessibilityAddTraits(.isHeader)
    }
}

struct StatTile: View {
    let label: String
    let value: String
    var icon: String? = nil
    var tint: Color = .accentColor
    var supporting: String? = nil
    var body: some View {
        Card(padding: 12) {
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    if let i = icon { Image(systemName: i).foregroundStyle(tint).font(.caption) }
                    Text(label).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }
                Text(value).font(DS.display(22)).lineLimit(1).minimumScaleFactor(0.6)
                if let s = supporting { Text(s).font(.caption2).foregroundStyle(.secondary).lineLimit(2) }
            }
        }
    }
}

struct LabeledRow: View {
    let label: String
    let value: String
    var valueColor: Color = .primary
    var body: some View {
        HStack {
            Text(label).foregroundStyle(.secondary)
            Spacer()
            Text(value).fontWeight(.medium).foregroundStyle(valueColor).multilineTextAlignment(.trailing)
        }
        .font(.subheadline)
        .padding(.vertical, 5)
    }
}

/// Sun path across the sky; at night a dimmed path and the Moon.
struct SunArc: View {
    let progress: Double?
    let nightProgress: Double?
    @Environment(\.colorScheme) var scheme
    var body: some View {
        GeometryReader { g in
            let w = g.size.width, h = g.size.height
            let horizonY = h * 0.86, rx = w / 2 * 0.9, ry = min(h * 0.78, rx), cx = w / 2
            ZStack {
                Path { p in p.move(to: CGPoint(x: 0, y: horizonY)); p.addLine(to: CGPoint(x: w, y: horizonY)) }
                    .stroke(Color.primary.opacity(0.25), lineWidth: 1.5)
                HalfEllipse(cx: cx, cy: horizonY, rx: rx, ry: ry, to: 1)
                    .stroke(Palette.golden.opacity(0.5), style: StrokeStyle(lineWidth: 2, dash: [6, 6]))
                if let f = progress {
                    HalfEllipse(cx: cx, cy: horizonY, rx: rx, ry: ry, to: f)
                        .stroke(LinearGradient(colors: [DS.sunrise(scheme), Palette.golden, DS.sunset(scheme)], startPoint: .leading, endPoint: .trailing),
                                style: StrokeStyle(lineWidth: 3.5, lineCap: .round))
                    let a = Double.pi + Double.pi * f
                    let p = CGPoint(x: cx + cos(a) * rx, y: horizonY + sin(a) * ry)
                    Circle().fill(RadialGradient(colors: [Palette.golden.opacity(0.55), .clear], center: .center, startRadius: 0, endRadius: 28))
                        .frame(width: 56, height: 56).position(p)
                    Circle().fill(DS.sunrise(scheme)).frame(width: 18, height: 18).position(p)
                    Circle().fill(Color.white.opacity(0.9)).frame(width: 8, height: 8).position(p)
                } else {
                    let a = Double.pi - Double.pi * (nightProgress ?? 0.5)
                    Circle().fill(DS.sunset(scheme).opacity(0.45)).frame(width: 10, height: 10).position(x: cx + cos(a) * rx, y: horizonY + sin(a) * ry * 0.12)
                    GlyphView(.moon, color: .primary.opacity(0.85)).frame(width: 26, height: 26).position(x: cx, y: horizonY - ry * 0.55)
                }
                Circle().fill(DS.sunrise(scheme)).frame(width: 6, height: 6).position(x: cx - rx, y: horizonY)
                Circle().fill(DS.sunset(scheme)).frame(width: 6, height: 6).position(x: cx + rx, y: horizonY)
            }
        }
        .accessibilityElement()
        .accessibilityLabel(progress.map { "The Sun is \(Int($0 * 100)) percent through today's daylight" } ?? "The Sun is below the horizon")
    }
}

struct HalfEllipse: Shape {
    let cx: CGFloat, cy: CGFloat, rx: CGFloat, ry: CGFloat, to: Double
    func path(in rect: CGRect) -> Path {
        var p = Path()
        for i in 0...60 {
            let a = Double.pi + Double.pi * to * Double(i) / 60
            let pt = CGPoint(x: cx + cos(a) * rx, y: cy + sin(a) * ry)
            if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        return p
    }
}

struct AnalogClock: View {
    let date: Date
    let zone: TimeZone
    let isDay: Bool
    @Environment(\.colorScheme) var scheme
    var body: some View {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = zone
        let c = cal.dateComponents([.hour, .minute, .second], from: date)
        let h = Double(c.hour ?? 0), m = Double(c.minute ?? 0), s = Double(c.second ?? 0)
        let hands: Color = isDay ? .primary : .white
        return ZStack {
            Circle().fill(isDay ? DS.card(scheme).opacity(0.6) : Palette.navy950)
            Circle().stroke(isDay ? DS.sunrise(scheme).opacity(0.6) : DS.blue(scheme).opacity(0.5), lineWidth: 2)
            ForEach(0..<12, id: \.self) { i in
                Rectangle().fill(hands.opacity(0.6)).frame(width: i % 3 == 0 ? 2 : 1, height: i % 3 == 0 ? 8 : 5)
                    .offset(y: -40).rotationEffect(.degrees(Double(i) * 30))
            }
            Capsule().fill(hands).frame(width: 3.5, height: 26).offset(y: -13).rotationEffect(.degrees((h.truncatingRemainder(dividingBy: 12)) * 30 + m * 0.5))
            Capsule().fill(hands).frame(width: 2.5, height: 36).offset(y: -18).rotationEffect(.degrees(m * 6 + s * 0.1))
            Capsule().fill(DS.sunset(scheme)).frame(width: 1, height: 38).offset(y: -19).rotationEffect(.degrees(s * 6))
            Circle().fill(DS.sunset(scheme)).frame(width: 6, height: 6)
        }
        .frame(width: 100, height: 100)
        .accessibilityLabel(String(format: "Analog clock %d:%02d", Int(h), Int(m)))
    }
}

struct SunPositionDial: View {
    let azimuth: Double
    let elevation: Double
    @Environment(\.colorScheme) var scheme
    var body: some View {
        HStack(spacing: 24) {
            ZStack {
                Circle().stroke(Color.secondary.opacity(0.4), lineWidth: 1.5)
                ForEach(["N", "E", "S", "W"].indices, id: \.self) { i in
                    Text(["N", "E", "S", "W"][i]).font(.caption2.bold()).foregroundStyle(.secondary)
                        .offset(y: -44).rotationEffect(.degrees(Double(i) * 90))
                }
                Capsule().fill((elevation > 0 ? DS.sunrise(scheme) : DS.blue(scheme)).opacity(0.6)).frame(width: 2, height: 34).offset(y: -17)
                    .rotationEffect(.degrees(azimuth))
                Circle().fill(elevation > 0 ? DS.sunrise(scheme) : DS.blue(scheme)).frame(width: 14, height: 14).offset(y: -34).rotationEffect(.degrees(azimuth))
            }
            .frame(width: 104, height: 104)
            .accessibilityLabel("Sun azimuth \(Int(azimuth)) degrees")
            ZStack(alignment: .bottomLeading) {
                Path { p in p.addArc(center: CGPoint(x: 60, y: 60), radius: 54, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false) }
                    .stroke(Color.secondary.opacity(0.4), lineWidth: 1.5)
                Path { p in p.move(to: CGPoint(x: 0, y: 60)); p.addLine(to: CGPoint(x: 120, y: 60)) }.stroke(Color.secondary, lineWidth: 1)
                let e = max(-20, min(90, elevation)) * .pi / 180
                Path { p in p.move(to: CGPoint(x: 60, y: 60)); p.addLine(to: CGPoint(x: 60 + cos(e) * 46, y: 60 - sin(e) * 46)) }
                    .stroke(elevation > 0 ? DS.sunrise(scheme) : DS.blue(scheme), lineWidth: 2)
                Circle().fill(elevation > 0 ? DS.sunrise(scheme) : DS.blue(scheme)).frame(width: 12, height: 12)
                    .position(x: 60 + cos(e) * 46, y: 60 - sin(e) * 46)
            }
            .frame(width: 120, height: 64)
            .accessibilityLabel("Sun elevation \(Int(elevation)) degrees")
        }
    }
}

/// Clock that ticks once per second.
struct Ticking<Content: View>: View {
    @ViewBuilder let content: (Date) -> Content
    var body: some View { TimelineView(.periodic(from: .now, by: 1)) { ctx in content(ctx.date) } }
}

struct Toast: View {
    let text: String
    var body: some View {
        Text(text).font(.subheadline.weight(.medium)).padding(.horizontal, 16).padding(.vertical, 10)
            .background(.ultraThinMaterial, in: Capsule()).padding(.bottom, 90)
            .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}

/// Sample wallpapers for the Widget Studio preview.
enum SampleWallpaper: String, CaseIterable, Identifiable {
    case photo = "Photo", dark = "Dark", light = "Light", colorful = "Colorful", gray = "Mid-gray"
    var id: String { rawValue }
    @ViewBuilder var view: some View {
        switch self {
        case .dark: LinearGradient(colors: [Color(hex: 0xFF14161F), Color(hex: 0xFF070809)], startPoint: .top, endPoint: .bottom)
        case .light: LinearGradient(colors: [Color(hex: 0xFFF7F4EF), Color(hex: 0xFFE6E1D8)], startPoint: .top, endPoint: .bottom)
        case .gray: Color(hex: 0xFF8A8A8A)
        case .colorful: LinearGradient(colors: [Color(hex: 0xFF3B1C5A), Color(hex: 0xFFE0566A), Color(hex: 0xFFFFB547)], startPoint: .topLeading, endPoint: .bottomTrailing)
        case .photo:
            ZStack {
                LinearGradient(colors: [Color(hex: 0xFF6FA8DC), Color(hex: 0xFFCFE6F5)], startPoint: .top, endPoint: .bottom)
                WallRidge(base: 0.62, amp: 0.10, f: 9).fill(Color(hex: 0xFF5D7A8C))
                WallRidge(base: 0.75, amp: 0.08, f: 13).fill(Color(hex: 0xFF3E5A3C))
                WallRidge(base: 0.88, amp: 0.05, f: 17).fill(Color(hex: 0xFF2C3E2A))
            }
        }
    }
}

struct WallRidge: Shape {
    let base: Double, amp: Double, f: Double
    func path(in r: CGRect) -> Path {
        var p = Path(); p.move(to: CGPoint(x: 0, y: r.height))
        for i in 0...60 {
            let t = Double(i) / 60
            p.addLine(to: CGPoint(x: t * r.width, y: r.height * base - r.height * amp * (sin(t * f) + 0.4 * sin(t * f * 2.7))))
        }
        p.addLine(to: CGPoint(x: r.width, y: r.height)); p.closeSubpath(); return p
    }
}

/// Typical iPhone widget sizes in points.
enum PreviewSize: String, CaseIterable, Identifiable {
    case small = "Small", medium = "Medium", large = "Large", lockRect = "Lock Screen"
    var id: String { rawValue }
    var size: CGSize {
        switch self {
        case .small: return CGSize(width: 170, height: 170)
        case .medium: return CGSize(width: 364, height: 170)
        case .large: return CGSize(width: 364, height: 382)
        case .lockRect: return CGSize(width: 170, height: 72)
        }
    }
    static func forClass(_ c: WidgetSizeClass) -> PreviewSize {
        switch c { case .small: return .medium; case .square: return .small; case .wide: return .medium; case .large: return .large }
    }
}
