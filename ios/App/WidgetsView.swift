import SwiftUI
import DayArcCore

extension AppModel {
    /// Face data from in-memory state (cheaper than re-reading the store for every preview).
    func faceData(for spec: WidgetSpec, now: Date) -> FaceData {
        let primary = SharedStore.resolveLocation(spec.locationId, in: locations) ?? active
        var others: [SavedLocation]
        if !spec.extraLocationIds.isEmpty {
            others = spec.extraLocationIds.compactMap { id in locations.first { $0.id == id } }
        } else {
            others = cityList.filter { $0.id != primary?.id }
                .sorted { ($0.isFavorite ? 0 : 1, $0.sortOrder) < ($1.isFavorite ? 0 : 1, $1.sortOrder) }
            others = Array(others.prefix(5))
        }
        if others.isEmpty && spec.layout == .multiCity {
            others = ["London", "Tokyo", "New York"].compactMap { n in CityDatabase.shared.search(n, limit: 1).first?.toLocation() }
        }
        return FaceData(primary: primary.map { FaceCity($0, now: now) },
                        others: others.filter { $0.id != primary?.id }.map { FaceCity($0, now: now) },
                        now: now, use24h: settings.use24h, dateStyle: settings.dateStyle)
    }
}

/// Preview of one face at a fixed point size.
struct FacePreview: View {
    @EnvironmentObject var model: AppModel
    let spec: WidgetSpec
    let size: CGSize
    var now: Date = Date()
    var body: some View {
        WidgetFace(spec: spec, data: model.faceData(for: spec, now: now))
            .frame(width: size.width, height: size.height)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

/// Scales a fixed-size preview into the space available.
struct ScaledPreview: View {
    let spec: WidgetSpec
    let size: CGSize
    let maxWidth: CGFloat
    var body: some View {
        let s = min(1, maxWidth / size.width)
        FacePreview(spec: spec, size: size)
            .scaleEffect(s, anchor: .topLeading)
            .frame(width: size.width * s, height: size.height * s, alignment: .topLeading)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

struct EditorTarget: Identifiable {
    let id = UUID()
    let designId: String?
    let spec: WidgetSpec
}

struct WidgetsView: View {
    @EnvironmentObject var model: AppModel
    @State private var editing: EditorTarget? = nil
    @State private var showGuide = false

    var body: some View {
        NavigationStack {
            TimelineView(.everyMinute) { _ in
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        guideCard
                        if !model.designs.isEmpty {
                            SectionTitle(title: "My designs")
                            LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 14) {
                                ForEach(model.designs) { d in
                                    let tsize = PreviewSize.forClass(WidgetTemplates.byId(d.spec.templateId)?.sizeClass ?? .square)
                                    Button { editing = EditorTarget(designId: d.id, spec: d.spec) } label: {
                                        tile(d.spec, tsize, d.spec.name, "Saved design")
                                    }
                                    .buttonStyle(.plain)
                                    .contextMenu {
                                        Button { editing = EditorTarget(designId: d.id, spec: d.spec) } label: { Label("Edit", systemImage: "slider.horizontal.3") }
                                        Button { model.duplicateDesign(d.id) } label: { Label("Duplicate", systemImage: "plus.square.on.square") }
                                        Button(role: .destructive) { model.deleteDesign(d.id) } label: { Label("Delete", systemImage: "trash") }
                                    }
                                }
                            }
                        }
                        ForEach([WidgetSizeClass.square, .small, .wide, .large], id: \.self) { c in
                            let items = WidgetTemplates.all.filter { $0.sizeClass == c }
                            if !items.isEmpty {
                                SectionTitle(title: c == .wide ? "Medium templates" : c == .large ? "Large templates" : c == .square ? "Small templates" : "Compact templates")
                                LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 14) {
                                    ForEach(items) { t in
                                        Button { editing = EditorTarget(designId: nil, spec: t.spec) } label: {
                                            tile(t.spec, PreviewSize.forClass(c), t.name, t.description)
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                            }
                        }
                    }
                    .padding(.horizontal, DS.screen).padding(.bottom, 24)
                }
            }
            .navigationTitle("Widget Studio")
            .fullScreenCover(item: $editing) { t in
                WidgetEditorView(designId: t.designId, initial: t.spec).environmentObject(model)
            }
            .sheet(isPresented: $showGuide) { WidgetGuide() }
            .onAppear {
                let a = ProcessInfo.processInfo.arguments
                if editing == nil, let i = a.firstIndex(of: "-tab"), i + 1 < a.count, a[i + 1] == "editor" {
                    editing = EditorTarget(designId: nil, spec: WidgetTemplates.all[1].spec)
                }
            }
        }
    }

    private func tile(_ spec: WidgetSpec, _ size: PreviewSize, _ title: String, _ subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            GeometryReader { g in
                ZStack {
                    SampleWallpaper.photo.view.opacity(0.9)
                    ScaledPreview(spec: spec, size: size.size, maxWidth: g.size.width - 16)
                }
                .frame(width: g.size.width, height: g.size.height)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .aspectRatio(1.05, contentMode: .fit)
            Text(title).font(.subheadline.weight(.semibold)).lineLimit(1)
            Text(subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(2)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(title). \(subtitle)")
        .accessibilityAddTraits(.isButton)
    }

    private var guideCard: some View {
        Card {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "plus.rectangle.on.rectangle").font(.title2).foregroundStyle(Palette.amber)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Design here, then add it to your Home Screen").font(.subheadline.weight(.semibold))
                    Text("Tap a template to customize it and save. Each widget you add can show a different design and city.")
                        .font(.caption).foregroundStyle(.secondary)
                    Button("How to add a widget") { showGuide = true }.font(.caption.weight(.semibold)).padding(.top, 2)
                }
            }
        }
        .padding(.top, 6)
    }
}

struct WidgetGuide: View {
    @Environment(\.dismiss) var dismiss
    var body: some View {
        NavigationStack {
            List {
                Section("Home Screen") {
                    step(1, "Touch and hold an empty spot on your Home Screen until the apps jiggle.")
                    step(2, "Tap Edit (or +) in the top corner, then tap Add Widget.")
                    step(3, "Search for DayArc and pick Small, Medium or Large. Tap Add Widget.")
                    step(4, "Touch and hold the new widget → Edit Widget. Choose your saved Design and, if you like, a City.")
                }
                Section("Lock Screen") {
                    step(1, "Touch and hold the Lock Screen, tap Customize → Lock Screen.")
                    step(2, "Tap the widget area and choose DayArc (circle, rectangle or inline).")
                }
                Section("Good to know") {
                    Text("Add as many DayArc widgets as you like — each keeps its own design and city.")
                    Text("iOS refreshes widgets on its own schedule. The clock and countdowns tick live; sunrise and sunset update automatically.")
                    Text("If a widget shows only templates, open DayArc once so it can share your saved designs.")
                }
                .font(.subheadline).foregroundStyle(.secondary)
            }
            .navigationTitle("Add a widget")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }

    private func step(_ n: Int, _ t: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Text("\(n)").font(.caption.bold()).frame(width: 22, height: 22).background(Circle().fill(Palette.amber.opacity(0.25)))
            Text(t).font(.subheadline)
        }
    }
}
