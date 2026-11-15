import SwiftUI

/// The twelve layout engines. Each adapts to the real widget size (small, medium, large, 1×1-like).
struct FaceContent: View {
    let spec: WidgetSpec
    let data: FaceData
    let city: FaceCity
    let size: CGSize

    var st: FaceStyle { FaceStyle(spec: spec) }
    var col: ColorSpec { spec.colors }
    var snap: SunSnapshot { city.snap }
    var z: TimeZone { city.zone }
    var now: Date { data.now }
    var fmt: TimeFormatter { TimeFormatter(use24h: data.use24h, dateStyle: data.dateStyle) }
    func has(_ e: WidgetElement) -> Bool { spec.has(e) }

    func timeStr(_ d: Date?) -> String {
        if let d = d { return fmt.time(d, z) }
        switch snap.today.dayType { case .polarDay: return "No set"; case .polarNight: return "No rise"; case .normal: return "—" }
    }

    var body: some View {
        GeometryReader { g in
            let w = g.size.width, h = g.size.height
            Group {
                switch spec.layout {
                case .stack: stack(w, h)
                case .clock: clockLayout(w, h)
                case .solarDial: SolarDialView(content: self, size: size)
                case .progressRing: ProgressRingView(content: self, size: size)
                case .arc: arcLayout(w, h)
                case .bar: barLayout(w, h)
                case .dashboard: dashboard(w, h)
                case .multiCity: multiCity(w, h)
                case .golden: golden(w, h)
                case .icon: iconLayout(w, h)
                case .split: split(w, h)
                case .nextEvent: nextEvent(w, h)
                }
            }
            .frame(width: w, height: h)
        }
    }

    // MARK: building blocks

    struct Item: Identifiable { let id = UUID(); let glyph: Glyph; let label: String; let value: String; let color: ARGB }

    var sunItems: [Item] {
        var items: [Item] = []
        if has(.sunrise) { items.append(Item(glyph: .sunrise, label: "Sunrise", value: timeStr(snap.today.sunrise), color: col.sunrise)) }
        if has(.sunset) { items.append(Item(glyph: .sunset, label: "Sunset", value: timeStr(snap.today.sunset), color: col.sunset)) }
        if has(.daylight) { items.append(Item(glyph: .hourglass, label: "Daylight", value: Durations.hm(snap.today.daylight), color: col.accent)) }
        if has(.solarNoon) { items.append(Item(glyph: .sun, label: "Solar noon", value: fmt.time(snap.today.solarNoon, z), color: col.icon)) }
        if has(.goldenHour), let gw = snap.nextGoldenHour {
            items.append(Item(glyph: .sun, label: "Golden hour", value: "\(fmt.time(gw.start, z))–\(fmt.time(gw.end, z))", color: col.sunrise))
        }
        if has(.nextEvent), let n = snap.nextSunEvent {
            let rise = n.type == .sunrise
            items.append(Item(glyph: rise ? .sunrise : .sunset, label: rise ? "Next sunrise" : "Next sunset", value: fmt.time(n.date, z), color: rise ? col.sunrise : col.sunset))
        }
        if has(.countdownSunrise), let r = snap.next.nextSunrise {
            items.append(Item(glyph: .sunrise, label: "Sunrise in", value: String(Durations.countdown(from: now, to: r).dropFirst(3)), color: col.sunrise))
        }
        if has(.countdownSunset), let s = snap.next.nextSunset {
            items.append(Item(glyph: .sunset, label: "Sunset in", value: String(Durations.countdown(from: now, to: s).dropFirst(3)), color: col.sunset))
        }
        return items
    }

    /// [glyph] LABEL ....... value   (or centered glyph + value without a label)
    @ViewBuilder func infoRow(_ item: Item, height: CGFloat, align: WidgetAlign, showLabel: Bool = true) -> some View {
        let gs = min(height * 0.78, 26)
        let icons = has(.icons)
        if align == .center || !showLabel {
            HStack(spacing: 6) {
                if align == .end { Spacer(minLength: 0) }
                if icons { st.glyph(item.glyph, item.color, gs) }
                Text(st.cased(item.value, .value)).font(st.font(.value, height * 0.62)).foregroundColor(col.text.opacity(spec.textOpacity))
                    .lineLimit(1).minimumScaleFactor(0.3).shadow(color: spec.typography.shadow ? .black.opacity(0.5) : .clear, radius: 3, y: 1)
                if align == .start { Spacer(minLength: 0) }
            }
            .frame(maxWidth: .infinity, alignment: align.frameAlignment)
        } else {
            HStack(spacing: 8) {
                if icons && align == .start { st.glyph(item.glyph, item.color, gs) }
                if align == .start {
                    st.text(item.label, .label, height * 0.42, col.secondaryText, align: .start).layoutPriority(0)
                    st.text(item.value, .value, height * 0.62, col.text, align: .end).fixedSize().layoutPriority(1)
                } else {
                    st.text(item.value, .value, height * 0.62, col.text, align: .start).fixedSize().layoutPriority(1)
                    st.text(item.label, .label, height * 0.42, col.secondaryText, align: .end).layoutPriority(0)
                }
                if icons && align == .end { st.glyph(item.glyph, item.color, gs) }
            }
        }
    }

    /// Current time: a big clock with a smaller AM/PM.
    @ViewBuilder func clock(_ height: CGFloat, align: WidgetAlign, zone: TimeZone? = nil, showAmPm: Bool = true) -> some View {
        let (main, ampm) = fmt.clockParts(now, zone ?? z)
        HStack(alignment: .firstTextBaseline, spacing: 3) {
            if align != .start { Spacer(minLength: 0) }
            Text(main).font(st.font(.clock, height * 0.95)).tracking(height * spec.typography.letterSpacing)
                .foregroundColor(col.text.opacity(spec.textOpacity)).lineLimit(1).minimumScaleFactor(0.2)
                .shadow(color: spec.typography.shadow ? .black.opacity(0.5) : .clear, radius: 3, y: 1)
            if let a = ampm, showAmPm {
                Text(a).font(st.font(.label, height * 0.3)).foregroundColor(col.text.opacity(spec.textOpacity)).lineLimit(1).fixedSize()
            }
            if align != .end { Spacer(minLength: 0) }
        }
    }

    /// Countdown: ticking system timer in real widgets when enabled, otherwise "2h 05m".
    @ViewBuilder func countdown(_ target: Date?, _ height: CGFloat, role: FaceStyle.Role, align: WidgetAlign) -> some View {
        if let t = target {
            if data.isWidget && spec.liveCountdown && t > now {
                Text(timerInterval: now...t, countsDown: true)
                    .font(st.font(role, height * 0.9)).foregroundColor(col.text.opacity(spec.textOpacity))
                    .multilineTextAlignment(align == .start ? .leading : align == .end ? .trailing : .center)
                    .lineLimit(1).minimumScaleFactor(0.3)
                    .frame(maxWidth: .infinity, alignment: align.frameAlignment)
            } else {
                st.text(role == .clock ? Durations.clock(t.timeIntervalSince(now)) : String(Durations.countdown(from: now, to: t).replacingOccurrences(of: "in ", with: "")),
                        role, height * 0.9, col.text, align: align)
            }
        } else {
            st.text("—", role, height * 0.9, col.text, align: align)
        }
    }

    func rows(_ h: CGFloat, _ weights: [CGFloat], loose: Bool = false) -> [CGFloat] {
        let gap = 4 * spec.spacing
        let total = weights.reduce(0, +)
        guard total > 0 else { return [] }
        let avail = h - gap * CGFloat(max(0, weights.count - 1))
        let unit = loose ? avail / total : min(avail / total, 30 * max(0.6, spec.spacing))
        return weights.map { $0 * unit }
    }

    var subtitleParts: String {
        [has(.country) && !city.country.isEmpty ? city.country : nil,
         has(.date) ? fmt.date(now, z) : nil,
         has(.tzAbbr) ? TimeFormatter.abbreviation(z, at: now) : nil].compactMap { $0 }.joined(separator: " · ")
    }

    // MARK: stack

    @ViewBuilder func stack(_ w: CGFloat, _ h: CGFloat) -> some View {
        let items = sunItems
        let horizontal = h < 64 && w > h * 1.8 && !items.isEmpty
        if horizontal && w / CGFloat(items.count) < 100 {
            HStack(spacing: 4) {
                ForEach(items) { it in
                    VStack(spacing: 2) {
                        if has(.icons) && h > 40 { st.glyph(it.glyph, it.color, min(h * 0.42, 26)) }
                        st.text(it.value, .value, h * 0.45, col.text)
                    }
                }
            }
        } else {
            let order = stackOrder(horizontal: horizontal, items: items)
            let weights: [CGFloat] = order.map { r -> CGFloat in
                switch r { case .city: return 1.1; case .sub: return 0.8; case .time: return 2.2; case .horizontal: return 1.6; case .item: return 1.0; case .progress: return 0.45 }
            }
            let hs = rows(h, weights)
            let gap = 4 * spec.spacing
            VStack(alignment: spec.align.hAlignment, spacing: gap) {
                ForEach(Array(order.enumerated()), id: \.offset) { i, kind in
                    let rh = i < hs.count ? hs[i] : 20
                    Group {
                        switch kind {
                        case .city: st.text(city.title, .title, rh * 0.8, col.text, align: spec.align)
                        case .sub: st.text(subtitleParts, .label, rh * 0.75, col.secondaryText, align: spec.align)
                        case .time: clock(rh, align: spec.align)
                        case .horizontal:
                            HStack { ForEach(items) { it in infoRow(it, height: rh, align: .center, showLabel: false) } }
                        case .item(let it): infoRow(it, height: rh, align: spec.align)
                        case .progress: ProgressBar(fraction: snap.daylightProgress, spec: spec)
                        }
                    }
                    .frame(height: rh)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    enum StackRow { case city, sub, time, horizontal, item(Item), progress }

    func stackOrder(horizontal: Bool, items: [Item]) -> [StackRow] {
        var o: [StackRow] = []
        if has(.city) { o.append(.city) }
        if !subtitleParts.isEmpty { o.append(.sub) }
        if has(.time) { o.append(.time) }
        if horizontal { o.append(.horizontal) } else { o += items.map { .item($0) } }
        if has(.progress) { o.append(.progress) }
        return o
    }

    // MARK: clock

    @ViewBuilder func clockLayout(_ w: CGFloat, _ h: CGFloat) -> some View {
        let pairs = sunItems.filter { $0.label == "Sunrise" || $0.label == "Sunset" }
        let sub = [has(.city) ? city.title : nil, has(.date) ? fmt.date(now, z) : nil, has(.tzAbbr) ? TimeFormatter.abbreviation(z, at: now) : nil]
            .compactMap { $0 }.joined(separator: " · ")
        if min(w, h) < 76 && w / h < 1.6 {
            VStack(spacing: 2) {
                if has(.time) { clock(h * 0.6, align: .center, showAmPm: false).frame(height: h * 0.62) }
                if let n = snap.nextSunEvent {
                    infoRow(Item(glyph: n.type == .sunrise ? .sunrise : .sunset, label: "", value: fmt.time(n.date, z), color: n.type == .sunrise ? col.sunrise : col.sunset),
                            height: h * 0.32, align: .center, showLabel: false)
                }
            }
        } else if w / h > 2.3 && !pairs.isEmpty {
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 0) {
                    if has(.time) { clock((sub.isEmpty ? h : h * 0.68) * 0.9, align: .start).frame(height: sub.isEmpty ? h : h * 0.68) }
                    if !sub.isEmpty { st.text(sub, .label, 14, col.secondaryText, align: .start) }
                }
                .frame(width: w * 0.62)
                VStack(spacing: 4) {
                    ForEach(pairs) { it in infoRow(it, height: min(h / CGFloat(pairs.count), 34), align: .end, showLabel: false) }
                }
            }
        } else {
            let weights: [CGFloat] = [has(.time) ? 2.6 : 0, sub.isEmpty ? 0 : 0.75, pairs.isEmpty ? 0 : 1.1, has(.progress) ? 0.4 : 0]
            let hs = rows(h, weights, loose: true)
            VStack(spacing: 3 * spec.spacing) {
                if has(.time) { clock(hs[0], align: spec.align).frame(height: hs[0]) }
                if !sub.isEmpty { st.text(sub, .label, hs[1] * 0.8, col.secondaryText, align: spec.align).frame(height: hs[1]) }
                if !pairs.isEmpty {
                    HStack(spacing: 4) { ForEach(pairs) { it in infoRow(it, height: hs[2], align: .center, showLabel: false) } }.frame(height: hs[2])
                }
                if has(.progress) { ProgressBar(fraction: snap.daylightProgress, spec: spec).frame(height: hs[3]) }
            }
        }
    }

    // MARK: arc

    @ViewBuilder func arcLayout(_ w: CGFloat, _ h: CGFloat) -> some View {
        let topH: CGFloat = (has(.city) || has(.progress) || has(.time)) ? min(h * 0.2, 22) : 0
        let bottomH: CGFloat = (has(.sunrise) || has(.sunset)) ? min(h * 0.24, 26) : 0
        VStack(spacing: 4) {
            if topH > 0 {
                HStack {
                    if has(.city) { st.text(city.title, .title, topH * 0.8, col.text, align: .start) }
                    if has(.time) { clock(topH, align: .end) }
                    else if has(.progress) {
                        let p = snap.daylightProgress
                        st.text(p != nil ? "\(Int((p! * 100).rounded()))% of daylight" : "Night", .label, topH * 0.7, col.secondaryText, align: .end)
                    }
                }.frame(height: topH)
            }
            SunArcView(snap: snap, spec: spec)
            if bottomH > 0 {
                HStack {
                    if has(.sunrise) { infoRow(Item(glyph: .sunrise, label: "", value: timeStr(snap.today.sunrise), color: col.sunrise), height: bottomH, align: .start, showLabel: false) }
                    if has(.sunset) { infoRow(Item(glyph: .sunset, label: "", value: timeStr(snap.today.sunset), color: col.sunset), height: bottomH, align: .end, showLabel: false) }
                }.frame(height: bottomH)
            }
        }
    }

    // MARK: bar

    @ViewBuilder func barLayout(_ w: CGFloat, _ h: CGFloat) -> some View {
        let showLabel = h > 60
        let rowH = showLabel ? h * 0.62 : h
        VStack(spacing: 2) {
            if showLabel {
                let label: String = {
                    if has(.city) { return city.title }
                    if let n = snap.nextSunEvent { return (n.type == .sunrise ? "Sunrise " : "Sunset ") + Durations.countdown(from: now, to: n.date) }
                    return ""
                }()
                st.text(label, .label, (h - rowH) * 0.8, col.secondaryText).frame(height: h - rowH)
            }
            if w < 230 {
                VStack(spacing: 3) {
                    HStack(spacing: 4) {
                        ForEach([Item(glyph: .sunrise, label: "", value: timeStr(snap.today.sunrise), color: col.sunrise),
                                 Item(glyph: .sunset, label: "", value: timeStr(snap.today.sunset), color: col.sunset)]) { it in
                            VStack(spacing: 1) {
                                if has(.icons) && rowH > 36 { st.glyph(it.glyph, it.color, min(rowH * 0.4, 24)) }
                                st.text(it.value, .value, rowH * 0.4, col.text)
                            }
                        }
                    }
                    if has(.progress) { ProgressBar(fraction: snap.daylightProgress, spec: spec).frame(height: min(rowH * 0.18, 8)) }
                }.frame(height: rowH)
            } else {
                HStack(spacing: 8) {
                    infoRow(Item(glyph: .sunrise, label: "", value: timeStr(snap.today.sunrise), color: col.sunrise), height: rowH * 0.8, align: .start, showLabel: false)
                        .frame(width: min(w * 0.34, 120))
                    if has(.progress) { ProgressBar(fraction: snap.daylightProgress, spec: spec).frame(height: min(rowH * 0.5, 12)) }
                    else { st.text(Durations.hm(snap.today.daylight), .label, rowH * 0.4, col.secondaryText) }
                    infoRow(Item(glyph: .sunset, label: "", value: timeStr(snap.today.sunset), color: col.sunset), height: rowH * 0.8, align: .end, showLabel: false)
                        .frame(width: min(w * 0.34, 120))
                }.frame(height: rowH)
            }
        }
    }

    // MARK: dashboard

    @ViewBuilder func dashboard(_ w: CGFloat, _ h: CGFloat) -> some View {
        let large = h > 200
        let headerH: CGFloat = min(large ? 40 : 24, h * 0.22)
        let tiles = dashboardTiles()
        VStack(spacing: 6) {
            if has(.city) || has(.time) || has(.date) {
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 0) {
                        if has(.city) { st.text(city.title, .title, headerH * (large && has(.date) ? 0.55 : 0.75), col.text, align: .start) }
                        if large && has(.date) { st.text(fmt.date(now, z), .label, headerH * 0.35, col.secondaryText, align: .start) }
                    }
                    if has(.time) { clock(headerH, align: .end).frame(width: w * 0.42) }
                }.frame(height: headerH)
            }
            if large && has(.sunArc) { SunArcView(snap: snap, spec: spec).frame(height: (h - headerH) * 0.36) }
            GeometryReader { g in
                let cols = (g.size.width / max(1, g.size.height) > 3.2) ? min(tiles.count, 4) : (tiles.count <= 2 ? max(1, tiles.count) : 2)
                let rowsN = max(1, (tiles.count + cols - 1) / cols)
                let th = min((g.size.height - 6 * CGFloat(rowsN - 1)) / CGFloat(rowsN), 64)
                VStack(spacing: 6) {
                    ForEach(0..<rowsN, id: \.self) { r in
                        HStack(spacing: 6) {
                            ForEach(0..<cols, id: \.self) { c in
                                let i = r * cols + c
                                if i < tiles.count { tile(tiles[i], th) } else { Color.clear }
                            }
                        }.frame(height: th)
                    }
                }
                .frame(maxHeight: .infinity)
            }
        }
    }

    func dashboardTiles() -> [Item] {
        var t: [Item] = []
        if has(.sunrise) { t.append(Item(glyph: .sunrise, label: "Sunrise", value: timeStr(snap.today.sunrise), color: col.sunrise)) }
        if has(.sunset) { t.append(Item(glyph: .sunset, label: "Sunset", value: timeStr(snap.today.sunset), color: col.sunset)) }
        if has(.daylight) { t.append(Item(glyph: .hourglass, label: "Daylight", value: Durations.hm(snap.today.daylight), color: col.accent)) }
        if has(.nextEvent), let n = snap.nextSunEvent {
            let r = n.type == .sunrise
            t.append(Item(glyph: r ? .sunrise : .sunset, label: r ? "Sunrise in" : "Sunset in",
                          value: Durations.countdown(from: now, to: n.date).replacingOccurrences(of: "in ", with: ""), color: r ? col.sunrise : col.sunset))
        }
        if has(.solarNoon) { t.append(Item(glyph: .sun, label: "Solar noon", value: fmt.time(snap.today.solarNoon, z), color: col.icon)) }
        if has(.goldenHour), let gw = snap.nextGoldenHour { t.append(Item(glyph: .sun, label: "Golden hour", value: fmt.time(gw.start, z), color: col.sunrise)) }
        return t
    }

    @ViewBuilder func tile(_ it: Item, _ th: CGFloat) -> some View {
        let inner = th - 10
        ZStack {
            RoundedRectangle(cornerRadius: 12, style: .continuous).fill(col.text.opacity(0.08 * spec.decorOpacity))
            if inner < 40 {
                HStack(spacing: 5) {
                    if has(.icons) { st.glyph(it.glyph, it.color, min(inner * 0.7, 18)) }
                    st.text(it.value, .value, inner * 0.6, col.text, align: .start)
                }.padding(.horizontal, 8)
            } else {
                let gs = min(inner * 0.5, 20)
                VStack(alignment: .leading, spacing: 1) {
                    HStack(spacing: 5) {
                        if has(.icons) { st.glyph(it.glyph, it.color, gs) }
                        st.text(it.label, .label, gs * 0.62, col.secondaryText, align: .start)
                    }.frame(height: gs)
                    st.text(it.value, .value, (inner - gs) * 0.85, col.text, align: .start)
                }.padding(.horizontal, 8).padding(.vertical, 5)
            }
        }
    }

    // MARK: multi-city

    @ViewBuilder func multiCity(_ w: CGFloat, _ h: CGFloat) -> some View {
        let cities = [city] + data.others
        let header = h > 200 && CGFloat(cities.count) * 52 < h - 40
        let areaH = header ? h - 36 : h
        let n = max(1, min(cities.count, Int(areaH / 30)))
        let rowH = min(areaH / CGFloat(n), 52)
        VStack(spacing: 0) {
            if header {
                HStack {
                    st.text(has(.sunrise) || has(.sunset) ? "Sunrise & sunset" : "World clock", .title, 18, col.text, align: .start)
                    st.text(fmt.date(now, z), .label, 13, col.secondaryText, align: .end)
                }.frame(height: 30).padding(.bottom, 6)
            }
            Spacer(minLength: 0)
            ForEach(0..<n, id: \.self) { i in
                let c = cities[i]
                VStack(spacing: 0) {
                    if i > 0 { Rectangle().fill(col.text.opacity(0.1 * spec.decorOpacity)).frame(height: 1) }
                    cityRow(c, rowH)
                }
            }
            Spacer(minLength: 0)
        }
    }

    @ViewBuilder func cityRow(_ c: FaceCity, _ rowH: CGFloat) -> some View {
        let showSun = has(.sunrise) || has(.sunset)
        let sub = [has(.country) && !c.country.isEmpty ? c.country : nil, has(.tzAbbr) ? TimeFormatter.abbreviation(c.zone, at: now) : nil,
                   has(.date) ? fmt.date(now, c.zone) : nil].compactMap { $0 }.joined(separator: " · ")
        HStack(spacing: 8) {
            if has(.dayNight) { st.glyph(c.snap.isDay ? .sun : .moon, c.snap.isDay ? col.icon : col.accent, min(rowH * 0.42, 16)) }
            VStack(alignment: .leading, spacing: 0) {
                st.text(c.title, .title, rowH * (sub.isEmpty || rowH <= 36 ? 0.36 : 0.32), col.text, align: .start)
                if !sub.isEmpty && rowH > 36 { st.text(sub, .label, rowH * 0.22, col.secondaryText, align: .start) }
            }
            if has(.time) { clock(rowH * 0.7, align: .end, zone: c.zone).frame(width: size.width * 0.3) }
            if showSun {
                VStack(alignment: .trailing, spacing: 0) {
                    if has(.sunrise) { st.text("↑" + fmt.time(c.snap.today.sunrise, c.zone), .value, rowH * 0.28, col.sunrise, align: .end) }
                    if has(.sunset) { st.text("↓" + fmt.time(c.snap.today.sunset, c.zone), .value, rowH * 0.28, col.sunset, align: .end) }
                }.frame(width: size.width * (has(.time) ? 0.24 : 0.36))
            }
        }.frame(height: rowH)
    }

    // MARK: golden

    @ViewBuilder func golden(_ w: CGFloat, _ h: CGFloat) -> some View {
        let today = snap.today
        let windows = [today.goldenMorning.map { ("Morning", $0) }, today.goldenEvening.map { ("Evening", $0) }].compactMap { $0 }
        let weights: [CGFloat] = [1.1, has(.city) ? 0.65 : 0] + (windows.isEmpty ? [1] : windows.map { _ in 1 }) + [has(.sunset) ? 0.75 : 0]
        let hs = rows(h, weights)
        VStack(alignment: .leading, spacing: 4 * spec.spacing) {
            HStack(spacing: 6) {
                if has(.icons) { st.glyph(.sun, col.icon, hs[0] * 0.9) }
                st.text(snap.inGoldenHour ? "Golden hour now" : "Golden hour", .title, hs[0] * 0.8, col.text, align: .start)
            }.frame(height: hs[0])
            if has(.city) { st.text(city.title + " · " + fmt.date(now, z), .label, hs[1] * 0.8, col.secondaryText, align: .start).frame(height: hs[1]) }
            if windows.isEmpty {
                st.text(today.dayType == .polarNight ? "No golden hour today" : "Sun stays high today", .value, hs[2] * 0.7, col.text, align: .start).frame(height: hs[2])
            }
            ForEach(Array(windows.enumerated()), id: \.offset) { i, pair in
                let rh = hs[2 + i]
                infoRow(Item(glyph: .sun, label: pair.0, value: "\(fmt.time(pair.1.start, z)) – \(fmt.time(pair.1.end, z))", color: col.sunrise), height: rh, align: .start)
                    .padding(.horizontal, pair.1.contains(now) ? 4 : 0)
                    .background(RoundedRectangle(cornerRadius: 8).fill(col.text.opacity(pair.1.contains(now) ? 0.12 * spec.decorOpacity : 0)))
                    .frame(height: rh)
            }
            if has(.sunset) { st.text("Sunset \(timeStr(today.sunset))", .label, hs.last! * 0.8, col.secondaryText, align: .start).frame(height: hs.last!) }
        }
        .frame(maxHeight: .infinity)
    }

    // MARK: icon

    @ViewBuilder func iconLayout(_ w: CGFloat, _ h: CGFloat) -> some View {
        let n = snap.nextSunEvent
        let rise = n?.type == .sunrise
        let tiny = min(w, h) < 70
        VStack(spacing: 2) {
            st.glyph(n == nil ? (snap.isDay ? .sun : .moon) : (rise ? .sunrise : .sunset), rise ? col.sunrise : col.sunset, h * 0.48)
            st.text(n.map { fmt.time($0.date, z) } ?? (snap.isDay ? "All day" : "No sun"), .value, h * (tiny ? 0.3 : 0.24), col.text)
            if !tiny, let n = n { st.text(Durations.countdown(from: now, to: n.date), .label, h * 0.13, col.secondaryText) }
        }
    }

    // MARK: split

    @ViewBuilder func split(_ w: CGFloat, _ h: CGFloat) -> some View {
        let showDaylight = has(.daylight) && h > 70
        VStack(spacing: 2) {
            HStack(spacing: 0) {
                side(.sunrise, "Sunrise", timeStr(snap.today.sunrise), col.sunrise, h * (showDaylight ? 0.76 : 1))
                Rectangle().fill(col.text.opacity(0.25 * spec.decorOpacity)).frame(width: 1).padding(.vertical, h * 0.12)
                side(.sunset, "Sunset", timeStr(snap.today.sunset), col.sunset, h * (showDaylight ? 0.76 : 1))
            }
            if showDaylight {
                st.text("\(Durations.hm(snap.today.daylight)) of daylight  ·  \(Durations.signedMinSec(snap.daylightChange))", .value, h * 0.14, col.secondaryText)
                    .frame(height: h * 0.22)
            }
        }
    }

    @ViewBuilder func side(_ g: Glyph, _ label: String, _ value: String, _ c: ARGB, _ h: CGFloat) -> some View {
        VStack(spacing: 2) {
            if has(.icons) { st.glyph(g, c, min(h * 0.3, 30)) }
            st.text(label, .label, h * 0.14, col.secondaryText)
            st.text(value, .value, h * 0.32, col.text).padding(.horizontal, 4)
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: next event

    @ViewBuilder func nextEvent(_ w: CGFloat, _ h: CGFloat) -> some View {
        if let n = snap.nextSunEvent {
            let rise = n.type == .sunrise
            let c = rise ? col.sunrise : col.sunset
            let name = rise ? "Sunrise" : "Sunset"
            if w / h > 2.4 || (h < 90 && w / h > 1.6) {
                HStack(spacing: 8) {
                    if has(.icons) { st.glyph(rise ? .sunrise : .sunset, c, min(h * 0.8, 32)) }
                    VStack(alignment: .leading, spacing: 0) {
                        st.text(name, .label, h * 0.32, col.secondaryText, align: .start)
                        st.text(fmt.time(n.date, z), .value, h * 0.4, col.text, align: .start)
                    }
                    countdown(n.date, h * 0.5, role: .value, align: .end)
                }
            } else {
                let hs = rows(h, [0.7, 2.2, 0.7, has(.city) ? 0.6 : 0], loose: true)
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        if has(.icons) { st.glyph(rise ? .sunrise : .sunset, c, hs[0]) }
                        st.text("\(name) in", .label, hs[0] * 0.8, col.secondaryText, align: .start)
                    }.frame(height: hs[0])
                    countdown(n.date, hs[1], role: .clock, align: .start).frame(height: hs[1])
                    st.text("at \(fmt.time(n.date, z))", .value, hs[2] * 0.85, c, align: .start).frame(height: hs[2])
                    if has(.city) { st.text(city.title, .label, hs[3] * 0.85, col.secondaryText, align: .start).frame(height: hs[3]) }
                }
            }
        } else {
            st.text(snap.isDay ? "Midnight sun" : "Polar night", .title, h * 0.3, col.text)
        }
    }
}

// MARK: - Graphic components

struct ProgressBar: View {
    let fraction: Double?
    let spec: WidgetSpec
    var body: some View {
        GeometryReader { g in
            let th = min(g.size.height * 0.5, 6)
            ZStack(alignment: .leading) {
                Capsule().fill(spec.colors.text.opacity(0.18 * spec.decorOpacity)).frame(height: th)
                if let f = fraction {
                    Capsule().fill(LinearGradient(colors: [spec.colors.sunrise.color, spec.colors.sunset.color], startPoint: .leading, endPoint: .trailing))
                        .frame(width: max(th, g.size.width * f), height: th)
                    Circle().fill(spec.colors.arc.color).frame(width: th * 1.8, height: th * 1.8)
                        .shadow(color: spec.colors.arc.opacity(spec.decorOpacity), radius: th * 1.5)
                        .offset(x: g.size.width * f - th * 0.9)
                }
            }
            .frame(height: g.size.height)
        }
    }
}

struct SunArcView: View {
    let snap: SunSnapshot
    let spec: WidgetSpec
    var body: some View {
        GeometryReader { g in
            let w = g.size.width, h = g.size.height
            let horizonY = h - h * 0.12
            let rx = w / 2 * 0.92, ry = min(h * 0.82, rx)
            let cx = w / 2
            let col = spec.colors
            ZStack {
                Path { p in p.move(to: CGPoint(x: 0, y: horizonY)); p.addLine(to: CGPoint(x: w, y: horizonY)) }
                    .stroke(col.text.opacity(0.35 * spec.decorOpacity), lineWidth: 1.2)
                ArcShape(cx: cx, cy: horizonY, rx: rx, ry: ry, from: 0, to: 1)
                    .stroke(col.arc.opacity(0.45 * max(0.3, spec.decorOpacity)), style: StrokeStyle(lineWidth: 2, dash: [4, 4]))
                if let f = snap.daylightProgress {
                    ArcShape(cx: cx, cy: horizonY, rx: rx, ry: ry, from: 0, to: f)
                        .stroke(LinearGradient(colors: [col.sunrise.color, col.arc.color, col.sunset.color], startPoint: .leading, endPoint: .trailing),
                                style: StrokeStyle(lineWidth: 3, lineCap: .round))
                    let a = Double.pi + Double.pi * f
                    let p = CGPoint(x: cx + cos(a) * rx, y: horizonY + sin(a) * ry)
                    Circle().fill(RadialGradient(colors: [col.arc.opacity(0.6 * spec.decorOpacity), .clear], center: .center, startRadius: 0, endRadius: 22))
                        .frame(width: 44, height: 44).position(p)
                    Circle().fill(col.icon.color).frame(width: 14, height: 14).position(p)
                    Circle().fill(Color.white.opacity(0.9)).frame(width: 7, height: 7).position(p)
                } else {
                    let np = snap.nightProgress ?? 0.5
                    let a = Double.pi - Double.pi * np
                    Circle().fill(col.sunset.opacity(0.5)).frame(width: 8, height: 8).position(x: cx + cos(a) * rx, y: horizonY + sin(a) * ry * 0.18)
                    GlyphView(.moon, color: col.text.opacity(0.85 * spec.textOpacity)).frame(width: 20, height: 20).position(x: cx, y: horizonY - ry * 0.55)
                }
            }
        }
    }
}

/// Part of the upper half-ellipse; t = 0 at the left horizon, 1 at the right.
struct ArcShape: Shape {
    let cx: CGFloat, cy: CGFloat, rx: CGFloat, ry: CGFloat, from: Double, to: Double
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let steps = 60
        for i in 0...steps {
            let t = from + (to - from) * Double(i) / Double(steps)
            let a = Double.pi + Double.pi * t
            let pt = CGPoint(x: cx + cos(a) * rx, y: cy + sin(a) * ry)
            if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        return p
    }
}

struct SolarDialView: View {
    let content: FaceContent
    let size: CGSize
    var body: some View {
        let spec = content.spec, col = spec.colors, snap = content.snap
        let d = min(size.width, size.height)
        let ringW = d * 0.075
        let rr = d / 2 - ringW / 2 - 6
        let curve = SolarService.shared.curve(snap.location, snap.localDate, points: 97).map { $0.1 }
        let dayStart = snap.localDate.start(in: snap.zone)
        let frac = min(1, max(0, snap.now.timeIntervalSince(dayStart) / snap.today.dayLength))
        ZStack {
            ForEach(0..<max(0, curve.count - 1), id: \.self) { i in
                Circle()
                    .trim(from: CGFloat(i) / CGFloat(curve.count - 1), to: CGFloat(i + 1) / CGFloat(curve.count - 1) + 0.002)
                    .stroke(skyColor(curve[i], col).opacity(max(0.25, spec.decorOpacity)), lineWidth: ringW)
                    .rotationEffect(.degrees(90))
                    .frame(width: rr * 2, height: rr * 2)
            }
            ForEach(Array(stride(from: 0, to: 24, by: 3)), id: \.self) { hh in
                Rectangle().fill(col.text.opacity(0.45 * spec.decorOpacity)).frame(width: 1.2, height: ringW * (hh % 6 == 0 ? 0.7 : 0.35))
                    .offset(y: rr - ringW * (hh % 6 == 0 ? 1.25 : 1.07))
                    .rotationEffect(.degrees(Double(hh) * 15))
            }
            let a = (90 + frac * 360) * Double.pi / 180
            Circle().fill(RadialGradient(colors: [(snap.isDay ? col.arc : col.accent).opacity(0.6 * spec.decorOpacity), .clear], center: .center, startRadius: 0, endRadius: ringW * 2.4))
                .frame(width: ringW * 4.8, height: ringW * 4.8).offset(x: cos(a) * rr, y: sin(a) * rr)
            Circle().fill(snap.isDay ? col.icon.color : col.text.color).frame(width: ringW * 1.5, height: ringW * 1.5).offset(x: cos(a) * rr, y: sin(a) * rr)
            Circle().fill(Color.white.opacity(0.9)).frame(width: ringW * 0.64, height: ringW * 0.64).offset(x: cos(a) * rr, y: sin(a) * rr)
            let inner = rr - ringW * 1.8
            VStack(spacing: 1) {
                if spec.has(.dayNight) { content.st.glyph(snap.isDay ? .sun : .moon, snap.isDay ? col.icon : col.accent, inner * 0.28) }
                if spec.has(.time) { content.clock(inner * 0.5, align: .center).frame(height: inner * 0.5) }
                if spec.has(.city) { content.st.text(content.city.title, .label, inner * 0.17, col.secondaryText) }
                let sun = [spec.has(.sunrise) ? "↑ " + content.timeStr(snap.today.sunrise) : nil, spec.has(.sunset) ? "↓ " + content.timeStr(snap.today.sunset) : nil]
                    .compactMap { $0 }.joined(separator: "   ")
                if !sun.isEmpty { content.st.text(sun, .value, inner * 0.17, col.secondaryText) }
            }
            .frame(width: inner * 1.6, height: inner * 1.3)
        }
        .frame(width: size.width, height: size.height)
        .position(x: size.width / 2 - content.contentOffset.width, y: size.height / 2 - content.contentOffset.height)
    }

    func skyColor(_ e: Double, _ col: ColorSpec) -> Color {
        if e >= SolarAltitude.goldenUpper { return col.arc.color }
        if e >= SolarAltitude.horizon { return col.sunrise.color }
        if e >= SolarAltitude.civil { return col.sunset.opacity(0.75) }
        if e >= SolarAltitude.astronomical { return col.accent.opacity(0.45) }
        return col.text.opacity(0.12)
    }
}

struct ProgressRingView: View {
    let content: FaceContent
    let size: CGSize
    var body: some View {
        let spec = content.spec, col = spec.colors, snap = content.snap
        let d = min(size.width, size.height)
        let ringW = d * (spec.shape == .ring ? 0.06 : 0.085)
        let rr = d / 2 - ringW / 2 - d * (spec.shape == .ring ? 0.045 : 0.07)
        let frac = snap.daylightProgress
        let isDay = frac != nil
        let f = frac ?? snap.nightProgress ?? 0
        let inner = rr - ringW
        ZStack {
            Circle().stroke(col.text.opacity(0.14 * max(0.3, spec.decorOpacity)), lineWidth: ringW).frame(width: rr * 2, height: rr * 2)
            Circle().trim(from: 0, to: f)
                .stroke(AngularGradient(colors: isDay ? [col.sunrise.color, col.arc.color, col.sunset.color] : [col.accent.color, col.accent.opacity(0.6)], center: .center),
                        style: StrokeStyle(lineWidth: ringW, lineCap: .round))
                .rotationEffect(.degrees(-90)).frame(width: rr * 2, height: rr * 2)
            let a = (-90 + 360 * f) * Double.pi / 180
            Circle().fill(Color.white).frame(width: ringW * 1.24, height: ringW * 1.24)
                .shadow(color: (isDay ? col.arc : col.accent).opacity(spec.decorOpacity), radius: ringW)
                .offset(x: cos(a) * rr, y: sin(a) * rr)
            VStack(spacing: 1) {
                if spec.has(.dayNight) { content.st.glyph(isDay ? .sun : .moon, isDay ? col.icon : col.accent, inner * 0.25) }
                if isDay {
                    content.st.text("\(Int((f * 100).rounded()))%", .clock, inner * 0.5, col.text)
                    content.st.text("of daylight", .label, inner * 0.15, col.secondaryText)
                    if spec.has(.sunset) { content.st.text("Sunset \(content.timeStr(snap.next.nextSunset))", .value, inner * 0.15, col.sunset) }
                } else {
                    content.st.text("Sunrise in", .label, inner * 0.15, col.secondaryText)
                    content.countdown(snap.next.nextSunrise, inner * 0.42, role: .clock, align: .center).frame(height: inner * 0.42)
                    if spec.has(.sunrise) || spec.has(.sunset) { content.st.text("at \(content.timeStr(snap.next.nextSunrise))", .value, inner * 0.15, col.sunrise) }
                }
            }
            .frame(width: inner * 1.5, height: inner * 1.3)
        }
        .frame(width: size.width, height: size.height)
        .position(x: size.width / 2 - content.contentOffset.width, y: size.height / 2 - content.contentOffset.height)
    }
}

extension FaceContent {
    /// Circular layouts are drawn relative to the whole widget, not the padded content area.
    var contentOffset: CGSize {
        let i = contentInsets(spec, size)
        return CGSize(width: i.leading, height: i.top)
    }
}
