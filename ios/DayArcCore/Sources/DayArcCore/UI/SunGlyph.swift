import SwiftUI

public enum Glyph { case sunrise, sunset, sun, moon, hourglass }

/// Hand-drawn glyphs matching the Android widget icons.
public struct GlyphView: View {
    let glyph: Glyph
    let color: Color
    public init(_ glyph: Glyph, color: Color) { self.glyph = glyph; self.color = color }

    public var body: some View {
        GeometryReader { g in
            let s = min(g.size.width, g.size.height)
            let r = s / 2
            let cx = g.size.width / 2, cy = g.size.height / 2
            let lw = max(1, s * 0.09)
            switch glyph {
            case .sunrise, .sunset:
                let horizonY = cy + r * 0.38, sunR = r * 0.42
                ZStack {
                    Path { p in p.addArc(center: CGPoint(x: cx, y: horizonY), radius: sunR, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false); p.closeSubpath() }
                        .fill(color)
                    Path { p in
                        for a in [180.0, 225, 270, 315, 360] {
                            let rad = a * .pi / 180
                            p.move(to: CGPoint(x: cx + cos(rad) * sunR * 1.35, y: horizonY + sin(rad) * sunR * 1.35))
                            p.addLine(to: CGPoint(x: cx + cos(rad) * sunR * 1.75, y: horizonY + sin(rad) * sunR * 1.75))
                        }
                        p.move(to: CGPoint(x: cx - r * 0.95, y: horizonY)); p.addLine(to: CGPoint(x: cx + r * 0.95, y: horizonY))
                        let ay = horizonY + r * 0.32
                        let rising = glyph == .sunrise
                        let tipY = rising ? ay - r * 0.04 : ay + r * 0.30
                        let baseY = tipY + (rising ? 1 : -1) * r * 0.22
                        p.move(to: CGPoint(x: cx - r * 0.2, y: baseY)); p.addLine(to: CGPoint(x: cx, y: tipY)); p.addLine(to: CGPoint(x: cx + r * 0.2, y: baseY))
                    }
                    .stroke(color, style: StrokeStyle(lineWidth: lw, lineCap: .round, lineJoin: .round))
                }
            case .sun:
                let sunR = r * 0.42
                ZStack {
                    Circle().fill(color).frame(width: sunR * 2, height: sunR * 2).position(x: cx, y: cy)
                    Path { p in
                        for i in 0..<8 {
                            let rad = Double(i) * 45 * .pi / 180
                            p.move(to: CGPoint(x: cx + cos(rad) * sunR * 1.4, y: cy + sin(rad) * sunR * 1.4))
                            p.addLine(to: CGPoint(x: cx + cos(rad) * sunR * 1.95, y: cy + sin(rad) * sunR * 1.95))
                        }
                    }.stroke(color, style: StrokeStyle(lineWidth: lw, lineCap: .round))
                }
            case .moon:
                MoonShape().fill(color, style: FillStyle(eoFill: true)).frame(width: s * 0.62, height: s * 0.62).position(x: cx, y: cy)
            case .hourglass:
                Path { p in
                    let w = r * 0.55, h = r * 0.8
                    p.move(to: CGPoint(x: cx - w, y: cy - h)); p.addLine(to: CGPoint(x: cx + w, y: cy - h))
                    p.addLine(to: CGPoint(x: cx - w, y: cy + h)); p.addLine(to: CGPoint(x: cx + w, y: cy + h)); p.closeSubpath()
                }.stroke(color, style: StrokeStyle(lineWidth: lw, lineCap: .round, lineJoin: .round))
            }
        }
        .accessibilityHidden(true)
    }
}

/// Crescent: a disc minus an offset disc (even-odd fill).
public struct MoonShape: Shape {
    public init() {}
    public func path(in rect: CGRect) -> Path {
        let r = min(rect.width, rect.height) / 2
        let c = CGPoint(x: rect.midX, y: rect.midY)
        var p = Path()
        p.addEllipse(in: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r))
        let cr = r * 0.85
        let cc = CGPoint(x: c.x + r * 0.55, y: c.y - r * 0.35)
        let cut = Path(ellipseIn: CGRect(x: cc.x - cr, y: cc.y - cr, width: 2 * cr, height: 2 * cr))
        return p.subtracting(cut)
    }
}
