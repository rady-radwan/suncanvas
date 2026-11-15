import SwiftUI
import DayArcCore

@main
struct DayArcApp: App {
    @StateObject private var model = AppModel()
    @Environment(\.scenePhase) private var phase

    init() { FontRegistry.registerAll() }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .onChange(of: phase) { _, p in
                    if p == .active { model.rescheduleReminders() }
                    if p == .background { model.widgetsChanged() }
                }
        }
    }
}

enum Tab: Hashable { case home, cities, calendar, widgets, settings }

struct RootView: View {
    @EnvironmentObject var model: AppModel
    @State private var tab: Tab = .home

    var body: some View {
        TimelineView(.everyMinute) { ctx in
            TabView(selection: $tab) {
                HomeView().tabItem { Label("Today", systemImage: "sun.horizon") }.tag(Tab.home)
                CitiesView().tabItem { Label("Cities", systemImage: "globe") }.tag(Tab.cities)
                CalendarScreen().tabItem { Label("Calendar", systemImage: "calendar") }.tag(Tab.calendar)
                WidgetsView().tabItem { Label("Widgets", systemImage: "square.grid.2x2") }.tag(Tab.widgets)
                SettingsView().tabItem { Label("Settings", systemImage: "gearshape") }.tag(Tab.settings)
            }
            .preferredColorScheme(scheme(at: ctx.date))
        }
        .tint(Palette.amber)
        .overlay(alignment: .bottom) {
            if let t = model.toast { Toast(text: t) }
        }
        .animation(.easeInOut(duration: 0.25), value: model.toast)
        .fullScreenCover(isPresented: Binding(get: { !model.settings.onboardingDone }, set: { _ in })) {
            OnboardingView().environmentObject(model)
        }
    }

    /// Theme: system / light / dark, or automatic by the Sun at the active place.
    private func scheme(at now: Date) -> ColorScheme? {
        switch model.settings.themeMode {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        case .sun:
            guard let l = model.active else { return nil }
            let p = SolarService.shared.position(l, at: now)
            return p.geometricElevation > SolarAltitude.horizon ? .light : .dark
        }
    }
}

struct OnboardingView: View {
    @EnvironmentObject var model: AppModel
    @State private var searching = false
    @State private var coords = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 22) {
                Spacer()
                Image("LogoMark").resizable().scaledToFit().frame(width: 120, height: 120)
                    .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                    .accessibilityHidden(true)
                Text("DayArc").font(DS.display(40, .bold))
                Text("Accurate sunrise and sunset for anywhere on Earth — calculated on your iPhone, no internet needed.")
                    .multilineTextAlignment(.center).foregroundStyle(.secondary).padding(.horizontal, 28)
                Spacer()
                VStack(spacing: 12) {
                    Button {
                        model.refreshLocation { ok in if ok { finish() } }
                    } label: {
                        HStack { if model.locating { ProgressView() }; Text("Use my current location") }
                            .frame(maxWidth: .infinity).padding(.vertical, 6)
                    }
                    .buttonStyle(.borderedProminent)
                    Button { searching = true } label: { Text("Search for a city").frame(maxWidth: .infinity).padding(.vertical, 6) }
                        .buttonStyle(.bordered)
                    Button("Enter coordinates") { coords = true }.padding(.top, 2)
                }
                .padding(.horizontal, 28)
                Text("Your location is used only when you ask, and never leaves your device.")
                    .font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center).padding(.horizontal, 28).padding(.bottom, 20)
            }
            .sheet(isPresented: $searching) {
                CitySearchView { loc in _ = model.add(loc, makeActive: true, makeDefault: true); searching = false; finish() }
                    .environmentObject(model)
            }
            .sheet(isPresented: $coords) {
                CoordinatesView { loc in _ = model.add(loc, makeActive: true, makeDefault: true); coords = false; finish() }
            }
        }
    }

    private func finish() { model.settings.onboardingDone = true; model.saveSettings() }
}
