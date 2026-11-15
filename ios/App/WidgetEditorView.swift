import SwiftUI
import PhotosUI
import DayArcCore

struct WidgetEditorView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) var dismiss
    @Environment(\.colorScheme) var scheme

    let designId: String?
    @State var spec: WidgetSpec
    private let original: WidgetSpec

    @State private var size: PreviewSize
    @State private var wallpaper: SampleWallpaper = .photo
    @State private var section: EditorSection = .style
    @State private var photoItem: PhotosPickerItem? = nil
    @State private var confirmDiscard = false

    enum EditorSection: String, CaseIterable, Identifiable {
        case style = "Style", background = "Background", text = "Text", content = "Content", place = "Place"
        var id: String { rawValue }
    }

    init(designId: String?, initial: WidgetSpec) {
        self.designId = designId
        _spec = State(initialValue: initial)
        original = initial
        _size = State(initialValue: PreviewSize.forClass(WidgetTemplates.byId(initial.templateId)?.sizeClass ?? .square))
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                previewArea
                Picker("Section", selection: $section) { ForEach(EditorSection.allCases) { Text($0.rawValue).tag($0) } }
                    .pickerStyle(.segmented).padding(.horizontal, DS.screen).padding(.vertical, 8)
                Form {
                    switch section {
                    case .style: styleSection
                    case .background: backgroundSection
                    case .text: textSection
                    case .content: contentSection
                    case .place: placeSection
                    }
                }
            }
            .navigationTitle(designId == nil ? "New widget" : "Edit widget")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { if spec != original { confirmDiscard = true } else { dismiss() } }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Menu {
                        Button { save(asNew: false) } label: { Label(designId == nil ? "Save design" : "Save changes", systemImage: "checkmark") }
                        if designId != nil { Button { save(asNew: true) } label: { Label("Save as new design", systemImage: "plus.square.on.square") } }
                        Button { spec = original } label: { Label("Reset changes", systemImage: "arrow.uturn.backward") }
                    } label: { Text("Save").bold() } primaryAction: { save(asNew: false) }
                }
            }
            .confirmationDialog("Discard your changes?", isPresented: $confirmDiscard, titleVisibility: .visible) {
                Button("Discard", role: .destructive) { dismiss() }
                Button("Keep editing", role: .cancel) {}
            }
            .onChange(of: photoItem) { _, item in loadPhoto(item) }
        }
    }

    private func save(asNew: Bool) {
        model.saveDesign(spec, id: asNew ? nil : designId)
        model.show("Saved “\(spec.name)” — choose it in Edit Widget on your Home Screen")
        dismiss()
    }

    // MARK: preview

    private var previewArea: some View {
        VStack(spacing: 8) {
            GeometryReader { g in
                ZStack {
                    wallpaper.view
                    TimelineView(.periodic(from: .now, by: 1)) { ctx in
                        let s = min(1, (g.size.width - 24) / size.size.width, (g.size.height - 16) / size.size.height)
                        FacePreview(spec: spec, size: size.size, now: ctx.date)
                            .scaleEffect(s)
                            .frame(width: size.size.width * s, height: size.size.height * s)
                            .shadow(color: .black.opacity(0.25), radius: 10, y: 4)
                    }
                }
                .frame(width: g.size.width, height: g.size.height)
            }
            .frame(height: size == .large ? 300 : 210)
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .padding(.horizontal, DS.screen)
            .accessibilityLabel("Live preview of \(spec.name)")
            HStack {
                Picker("Size", selection: $size) { ForEach(PreviewSize.allCases) { Text($0.rawValue).tag($0) } }
                Spacer()
                Picker("Wallpaper", selection: $wallpaper) { ForEach(SampleWallpaper.allCases) { Text($0.rawValue).tag($0) } }
            }
            .pickerStyle(.menu).font(.caption).padding(.horizontal, DS.screen)
            if let warn = contrastWarning {
                Label(warn, systemImage: "exclamationmark.triangle.fill").font(.caption).foregroundStyle(.orange).padding(.horizontal, DS.screen)
            }
        }
        .padding(.top, 8)
    }

    /// WCAG-style check of text against the dominant background color.
    private var contrastWarning: String? {
        let b = spec.background
        let base: ARGB
        switch b.type {
        case .solid, .gradient: base = b.color.over(ARGB(0xFF222222))
        case .multiGradient: base = (b.colors.first ?? b.color).over(ARGB(0xFF222222))
        case .glassLight: base = ARGB(0xFFE8E8E8)
        case .glassDark, .nightSky: base = ARGB(0xFF1A1D2A)
        case .sunrise: base = ARGB(0xFFFF9A55)
        case .sunset: base = ARGB(0xFF8E3B6A)
        default: return nil
        }
        let c = ARGB.contrast(spec.colors.text.over(base), base)
        return c < 3 ? String(format: "Low contrast (%.1f:1) — text may be hard to read", c) : nil
    }

    // MARK: sections

    @ViewBuilder private var styleSection: some View {
        Section {
            TextField("Design name", text: $spec.name)
            Picker("Layout", selection: $spec.layout) { ForEach(WidgetLayout.allCases) { Text($0.label).tag($0) } }
        }
        Section("Color palette") {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(WidgetPalettes.all) { p in
                        Button { spec = p.apply(to: spec) } label: {
                            VStack(spacing: 4) {
                                ZStack {
                                    LinearGradient(colors: [p.background.color, p.background2.color], startPoint: .topLeading, endPoint: .bottomTrailing)
                                    HStack(spacing: 3) {
                                        Circle().fill(p.colors.sunrise.color).frame(width: 9, height: 9)
                                        Circle().fill(p.colors.text.color).frame(width: 9, height: 9)
                                        Circle().fill(p.colors.sunset.color).frame(width: 9, height: 9)
                                    }
                                }
                                .frame(width: 54, height: 40).clipShape(RoundedRectangle(cornerRadius: 10))
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.primary.opacity(0.15)))
                                Text(p.name).font(.system(size: 9)).lineLimit(1).frame(width: 60)
                            }
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Palette \(p.name)")
                    }
                }
                .padding(.vertical, 4)
            }
        }
        Section("Shape") {
            Picker("Shape", selection: $spec.shape) { ForEach(WidgetShape.allCases) { Text($0.label).tag($0) } }
            if [.rounded, .custom, .cornerAccent].contains(spec.shape) {
                SliderRow(title: "Corner radius", value: $spec.cornerRadius, range: 0...60, step: 1, format: "%.0f pt")
            }
            SliderRow(title: "Padding", value: $spec.padding, range: 0...32, step: 1, format: "%.0f pt")
            SliderRow(title: "Spacing", value: $spec.spacing, range: 0.5...2, step: 0.05, format: "%.2f×")
            Picker("Alignment", selection: $spec.align) { ForEach(WidgetAlign.allCases) { Text($0.label).tag($0) } }.pickerStyle(.segmented)
        }
        Section("Border") {
            SliderRow(title: "Width", value: $spec.border.width, range: 0...6, step: 0.5, format: "%.1f pt")
            if spec.border.width > 0 {
                ColorRow(title: "Color", color: $spec.border.color)
                SliderRow(title: "Opacity", value: $spec.border.opacity, range: 0...1, step: 0.05, format: "%.0f%%", percent: true)
            }
        }
        Section("Visibility") {
            SliderRow(title: "Text opacity", value: $spec.textOpacity, range: 0.2...1, step: 0.05, format: "%.0f%%", percent: true)
            SliderRow(title: "Icons & arcs opacity", value: $spec.decorOpacity, range: 0...1, step: 0.05, format: "%.0f%%", percent: true)
        }
    }

    @ViewBuilder private var backgroundSection: some View {
        Section {
            Picker("Background", selection: $spec.background.type) { ForEach(BackgroundType.allCases) { Text($0.label).tag($0) } }
            if spec.background.type != .transparent {
                SliderRow(title: "Transparency", value: Binding(get: { 1 - spec.background.opacity }, set: { spec.background.opacity = 1 - $0 }),
                          range: 0...1, step: 0.05, format: "%.0f%%", percent: true)
            }
        } footer: {
            if spec.background.type == .transparent { Text("Fully transparent: only the text and icons show on your wallpaper.") }
            else if spec.background.type == .liveSky { Text("The sky color follows the real Sun at the widget's city — dawn, day, golden hour, dusk and night.") }
        }
        switch spec.background.type {
        case .solid:
            Section("Color") { ColorRow(title: "Background", color: $spec.background.color) }
        case .gradient:
            Section("Gradient") {
                ColorRow(title: "Start", color: $spec.background.color)
                ColorRow(title: "End", color: $spec.background.color2)
                SliderRow(title: "Angle", value: $spec.background.angle, range: 0...360, step: 5, format: "%.0f°")
            }
        case .multiGradient:
            Section("Colors") {
                ForEach(spec.background.colors.indices, id: \.self) { i in
                    ColorRow(title: "Color \(i + 1)", color: Binding(get: { spec.background.colors[min(i, spec.background.colors.count - 1)] },
                                                                    set: { if i < spec.background.colors.count { spec.background.colors[i] = $0 } }))
                }
                .onDelete { idx in if spec.background.colors.count - idx.count >= 2 { spec.background.colors.remove(atOffsets: idx) } }
                if spec.background.colors.count < 5 {
                    Button { spec.background.colors.append(ARGB(0xFFFFC870)) } label: { Label("Add color", systemImage: "plus") }
                }
                SliderRow(title: "Angle", value: $spec.background.angle, range: 0...360, step: 5, format: "%.0f°")
            }
        case .scene:
            Section("Scene") { Picker("Scene", selection: $spec.background.scene) { ForEach(SceneId.allCases) { Text($0.label).tag($0) } } }
        case .image:
            imageSection
        default:
            EmptyView()
        }
    }

    @ViewBuilder private var imageSection: some View {
        Section {
            PhotosPicker(selection: $photoItem, matching: .images) {
                Label(spec.background.imagePath == nil ? "Choose a photo" : "Replace photo", systemImage: "photo")
            }
            if spec.background.imagePath != nil {
                SliderRow(title: "Zoom", value: $spec.background.imageZoom, range: 1...3, step: 0.05, format: "%.2f×")
                SliderRow(title: "Move left / right", value: $spec.background.imageOffsetX, range: -1...1, step: 0.02, format: "%.2f")
                SliderRow(title: "Move up / down", value: $spec.background.imageOffsetY, range: -1...1, step: 0.02, format: "%.2f")
                SliderRow(title: "Brightness", value: $spec.background.imageBrightness, range: -0.6...0.6, step: 0.02, format: "%+.2f")
                SliderRow(title: "Photo opacity", value: $spec.background.imageOpacity, range: 0...1, step: 0.05, format: "%.0f%%", percent: true)
                ColorRow(title: "Tint", color: $spec.background.overlayColor)
                SliderRow(title: "Tint strength", value: $spec.background.overlayOpacity, range: 0...0.9, step: 0.05, format: "%.0f%%", percent: true)
                Button("Remove photo", role: .destructive) { spec.background.imagePath = nil }
            }
        } header: { Text("Photo") } footer: {
            Text("The photo is copied into DayArc and resized for the widget. A dark tint keeps text readable.")
        }
    }

    private func loadPhoto(_ item: PhotosPickerItem?) {
        guard let item else { return }
        Task {
            guard let data = try? await item.loadTransferable(type: Data.self), let img = UIImage(data: data) else {
                model.show("Couldn't load that photo"); return
            }
            // Store a downsized JPEG (≤1600 px) under the shared images folder; spec keeps a relative path.
            let maxSide: CGFloat = 1600
            let scale = min(1, maxSide / max(img.size.width, img.size.height))
            let target = CGSize(width: img.size.width * scale, height: img.size.height * scale)
            let fmt = UIGraphicsImageRendererFormat(); fmt.scale = 1
            let resized = UIGraphicsImageRenderer(size: target, format: fmt).image { _ in img.draw(in: CGRect(origin: .zero, size: target)) }
            guard let jpg = resized.jpegData(compressionQuality: 0.85) else { return }
            let name = "bg-\(UUID().uuidString.prefix(8)).jpg"
            do {
                try jpg.write(to: SharedStore.imagesDir.appendingPathComponent(name), options: .atomic)
                spec.background.imagePath = name
                spec.background.imageZoom = 1; spec.background.imageOffsetX = 0; spec.background.imageOffsetY = 0
            } catch { model.show("Couldn't save the photo") }
            photoItem = nil
        }
    }

    @ViewBuilder private var textSection: some View {
        Section("Font") {
            Picker("Font", selection: $spec.typography.font) {
                ForEach(WidgetFont.allCases) { f in Text(f.label).font(FontRegistry.font(f, .regular, size: 17)).tag(f) }
            }
            Picker("Weight", selection: $spec.typography.weight) { ForEach(FontWeightChoice.allCases) { Text($0.label).tag($0) } }.pickerStyle(.segmented)
            SliderRow(title: "Text size", value: $spec.typography.scale, range: 0.6...1.6, step: 0.05, format: "%.0f%%", percent: true)
            SliderRow(title: "Letter spacing", value: $spec.typography.letterSpacing, range: -0.05...0.3, step: 0.01, format: "%.2f")
            Picker("Text case", selection: $spec.typography.`case`) { ForEach(TextCase.allCases) { Text($0.label).tag($0) } }
            Toggle("Text shadow", isOn: $spec.typography.shadow)
        }
        Section("Clock") {
            Picker("Clock font", selection: Binding(get: { spec.typography.clockFont }, set: { spec.typography.clockFont = $0 })) {
                Text("Same as text").tag(WidgetFont?.none)
                ForEach(WidgetFont.allCases) { f in Text(f.label).tag(WidgetFont?.some(f)) }
            }
            Picker("Clock weight", selection: $spec.typography.clockWeight) { ForEach(FontWeightChoice.allCases) { Text($0.label).tag($0) } }.pickerStyle(.segmented)
            SliderRow(title: "Clock size", value: $spec.typography.clockScale, range: 0.5...1.8, step: 0.05, format: "%.0f%%", percent: true)
        }
        Section("Labels") {
            SliderRow(title: "Label size", value: $spec.typography.labelScale, range: 0.6...1.6, step: 0.05, format: "%.0f%%", percent: true)
            Picker("Label case", selection: $spec.typography.labelCase) { ForEach(TextCase.allCases) { Text($0.label).tag($0) } }
        }
        Section("Colors") {
            ColorRow(title: "Main text", color: $spec.colors.text)
            ColorRow(title: "Secondary text", color: $spec.colors.secondaryText)
            ColorRow(title: "Sunrise", color: $spec.colors.sunrise)
            ColorRow(title: "Sunset", color: $spec.colors.sunset)
            ColorRow(title: "Sun arc", color: $spec.colors.arc)
            ColorRow(title: "Icons", color: $spec.colors.icon)
            ColorRow(title: "Accent", color: $spec.colors.accent)
        }
    }

    @ViewBuilder private var contentSection: some View {
        Section {
            ForEach(WidgetElement.allCases) { e in
                Toggle(e.label, isOn: Binding(get: { spec.elements.contains(e) }, set: { on in if on { spec.elements.insert(e) } else { spec.elements.remove(e) } }))
            }
        } header: { Text("Show") } footer: {
            Text("Some layouts always show their main element (for example the clock in Clock hero). Small sizes hide lower-priority details automatically.")
        }
        Section("Live updates") {
            Toggle("Ticking clock", isOn: $spec.liveClock)
            Toggle("Ticking countdown", isOn: $spec.liveCountdown)
        }
    }

    @ViewBuilder private var placeSection: some View {
        Section {
            Picker("City", selection: Binding(get: { spec.locationId }, set: { spec.locationId = $0 })) {
                Text("Default place").tag(String?.none)
                ForEach(model.locations) { l in Text(l.headline).tag(String?.some(l.id)) }
            }
        } header: { Text("City") } footer: {
            Text("You can also pick a city for each widget in Edit Widget on the Home Screen — that choice wins over this one.")
        }
        if spec.layout == .multiCity {
            Section {
                ForEach(model.cityList) { l in
                    Toggle(l.displayName, isOn: Binding(
                        get: { spec.extraLocationIds.contains(l.id) },
                        set: { on in if on { if spec.extraLocationIds.count < 6 { spec.extraLocationIds.append(l.id) } } else { spec.extraLocationIds.removeAll { $0 == l.id } } }))
                }
            } header: { Text("Other cities") } footer: { Text("None selected = your favorite cities.") }
        }
    }
}

// MARK: - Controls

struct SliderRow: View {
    let title: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    let step: Double
    let format: String
    var percent = false
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(title)
                Spacer()
                Text(String(format: format, percent ? value * 100 : value)).foregroundStyle(.secondary).monospacedDigit()
            }
            .font(.subheadline)
            Slider(value: $value, in: range, step: step).accessibilityLabel(title)
        }
    }
}

struct ColorRow: View {
    let title: String
    @Binding var color: ARGB
    @State private var hex = ""
    @FocusState private var focused: Bool

    var body: some View {
        HStack {
            ColorPicker(title, selection: Binding(get: { color.color }, set: { color = ARGB(swiftUI: $0) }), supportsOpacity: true)
            TextField("#RRGGBB", text: $hex)
                .font(.caption.monospaced()).multilineTextAlignment(.trailing).frame(width: 96)
                .textInputAutocapitalization(.characters).autocorrectionDisabled()
                .focused($focused)
                .onSubmit { if let c = ARGB.parse(hex: hex) { color = c } else { hex = color.hex } }
        }
        .onAppear { hex = color.hex }
        .onChange(of: color) { _, c in if !focused { hex = c.hex } }
        .onChange(of: focused) { _, f in if !f { if let c = ARGB.parse(hex: hex) { color = c } else { hex = color.hex } } }
    }
}

extension ARGB {
    init(swiftUI c: Color) {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 1
        UIColor(c).getRed(&r, green: &g, blue: &b, alpha: &a)
        self.init(r: Double(max(0, min(1, r))), g: Double(max(0, min(1, g))), b: Double(max(0, min(1, b))), a: Double(a))
    }
}
