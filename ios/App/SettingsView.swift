import SwiftUI
import UniformTypeIdentifiers
import DayArcCore

struct BackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

struct SettingsView: View {
    @EnvironmentObject var model: AppModel
    @State private var exporting = false
    @State private var importing = false
    @State private var backupDoc: BackupDocument? = nil
    @State private var addingReminder = false

    private func bind<T>(_ kp: WritableKeyPath<AppSettings, T>) -> Binding<T> {
        Binding(get: { model.settings[keyPath: kp] }, set: { model.settings[keyPath: kp] = $0; model.saveSettings() })
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Appearance") {
                    Picker("Theme", selection: bind(\.themeMode)) { ForEach(ThemeMode.allCases) { Text($0.label).tag($0) } }
                    Picker("Time format", selection: Binding(get: { model.settings.timeFormat }, set: { model.settings.timeFormat = $0; model.saveSettings(); model.rescheduleReminders() })) {
                        ForEach(TimeFormatPref.allCases) { Text($0.label).tag($0) }
                    }
                    Picker("Date format", selection: bind(\.dateStyle)) { ForEach(DateStyle.allCases) { Text($0.label).tag($0) } }
                    Toggle("Show seconds on Today", isOn: bind(\.showSeconds))
                }

                Section {
                    Button { model.refreshLocation() } label: {
                        HStack { Label("Update current location", systemImage: "location"); Spacer(); if model.locating { ProgressView() } }
                    }
                    Toggle("Online city search", isOn: bind(\.onlineSearch))
                } header: { Text("Location") } footer: {
                    Text("Sunrise and sunset are always calculated on your iPhone. Online search only helps find small places not in the built-in list of 30,000+ cities.")
                }

                Section {
                    Toggle("Sun reminders", isOn: Binding(get: { model.settings.notificationsEnabled }, set: { model.setNotifications($0) }))
                    if model.settings.notificationsEnabled {
                        ForEach(model.reminders) { r in
                            Toggle(r.summary, isOn: Binding(get: { r.enabled }, set: { var x = r; x.enabled = $0; model.updateReminder(x) }))
                        }
                        .onDelete { idx in idx.map { model.reminders[$0].id }.forEach(model.deleteReminder) }
                        Button { addingReminder = true } label: { Label("Add reminder", systemImage: "plus") }
                    }
                } header: { Text("Reminders") } footer: {
                    Text("Reminders use your default place\(SharedStore.resolveLocation(nil, in: model.locations).map { " (\($0.displayName))" } ?? ""). iOS keeps up to 64 scheduled alerts, so DayArc refreshes them whenever you open the app.")
                }

                Section {
                    Button { backupDoc = BackupDocument(data: model.exportBackup()); exporting = true } label: { Label("Export backup", systemImage: "square.and.arrow.up") }
                    Button { importing = true } label: { Label("Restore from backup", systemImage: "square.and.arrow.down") }
                } header: { Text("Backup") } footer: {
                    Text("Saves your cities, widget designs, reminders and settings as a .json file — to Files, iCloud Drive or anywhere. Backups from DayArc for Android work too.")
                }

                Section("About") {
                    LabeledContent("Version", value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "—")
                    LabeledContent("Algorithm", value: "NOAA solar position")
                    LabeledContent("Accuracy", value: "≈ 1 minute or better")
                    LabeledContent("Widget data sharing", value: SharedStore.isShared ? "On" : "Off")
                    Text("All calculations happen on your device. DayArc has no ads, no accounts and no tracking.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Settings")
            .fileExporter(isPresented: $exporting, document: backupDoc, contentType: .json, defaultFilename: "DayArc-backup-\(LocalDate(Date(), in: .current))") { result in
                if case .success = result { model.show("Backup saved") }
            }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.json, .data]) { result in
                guard case .success(let url) = result else { return }
                let ok = url.startAccessingSecurityScopedResource()
                defer { if ok { url.stopAccessingSecurityScopedResource() } }
                do { model.show(try model.importBackup(try Data(contentsOf: url))) }
                catch { model.show("That file isn't a DayArc backup") }
            }
            .sheet(isPresented: $addingReminder) { ReminderEditor { model.addReminder($0); addingReminder = false } }
        }
    }
}

struct ReminderEditor: View {
    @Environment(\.dismiss) var dismiss
    let onSave: (Reminder) -> Void
    @State private var event: ReminderEvent = .sunset
    @State private var minutes = 30
    @State private var when = 0 // 0 before, 1 at, 2 after

    var body: some View {
        NavigationStack {
            Form {
                Picker("Event", selection: $event) { ForEach(ReminderEvent.allCases) { Text($0.label).tag($0) } }
                Picker("When", selection: $when) { Text("Before").tag(0); Text("At").tag(1); Text("After").tag(2) }.pickerStyle(.segmented)
                if when != 1 { Stepper("\(minutes) minutes", value: $minutes, in: 5...180, step: 5) }
            }
            .navigationTitle("New reminder")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") { onSave(Reminder(event: event, offsetMinutes: when == 1 ? 0 : (when == 0 ? -minutes : minutes))) }
                }
            }
        }
        .presentationDetents([.medium])
    }
}
