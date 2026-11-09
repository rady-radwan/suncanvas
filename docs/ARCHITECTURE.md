# DayArc — Architecture

## 1. Goals that shaped the design

1. **Accurate solar data, offline.** Everything astronomical is computed on the phone; the network is only used for optional, text-only city search.
2. **Beautiful, deeply customizable home-screen widgets** that stay correct without draining the battery.
3. **A real, maintainable Android codebase**: Kotlin, Jetpack Compose, Glance, Room, DataStore, WorkManager, Material 3.

## 2. Modules

| Module | Type | Responsibility |
|---|---|---|
| `:core-solar` | Pure Kotlin/JVM library | NOAA solar position, sunrise/sunset/twilight/golden & blue hour search, equinoxes/solstices. No Android dependency → fast JVM unit tests against JPL-ephemeris reference data. |
| `:app` | Android application | UI, persistence, location, time-zone resolution, widgets, reminders, backup. |

## 3. Package layout (`:app`)

```
app.suncanvas
├── DayArcApplication.kt     AppContainer (manual DI, lazy singletons)
├── MainActivity.kt             Theme selection (incl. sunrise/sunset auto-theme), widget tap handling
├── core/                       TimeFormatter (explicit zones, 12/24 h), durations, distance
├── data/
│   ├── db/                     Room: locations, solar_cache, widget_designs, widget_instances, reminders
│   ├── cities/                 Offline GeoNames city DB (assets/cities.tsv), Open-Meteo geocoder
│   ├── backup/                 JSON export/import through the Storage Access Framework
│   ├── SettingsRepository      DataStore preferences
│   ├── LocationRepository      Saved places, device location row, default/active selection
│   ├── SolarRepository         Memory LRU + Room cache in front of :core-solar; SunSnapshot
│   └── WidgetRepository        Saved designs + one independent spec per placed widget
├── location/                   One-shot foreground location (Fused / framework fallback), TimeZoneResolver
├── notify/                     Reminder scheduling (AlarmManager), reboot/time-change receiver
├── ui/
│   ├── theme/                  Design system (colors, type, shapes, spacing)
│   ├── components/             Sun arc, analog clock, sun-position dial, color picker, glyphs
│   └── screens/                home, onboarding, cities (search/coordinates/detail/world clock),
│                               calendar, widgets (gallery, editor, preview), settings
└── widget/
    ├── model/                  WidgetSpec (serializable design), 21 templates, 12 palettes
    ├── render/                 WidgetRenderer + WidgetPainter (shared by widgets and previews)
    ├── DayArcWidget.kt      Glance widget (bitmap face + live TextClock/Chronometer slots)
    ├── WidgetReceivers.kt      4 picker entries, updater, pin-result receiver
    ├── WidgetScheduler.kt      WorkManager: event-boundary job + periodic job
    └── WidgetConfigActivity.kt Per-widget editor launched by the launcher
```

State flows one way: Room/DataStore `Flow`s → `AppViewModel` `StateFlow`s → Compose. Screens never talk to the database directly except small, local lists.

## 4. Solar engine

* **Position:** NOAA Solar Calculator equations (Meeus): Julian century → mean longitude/anomaly → equation of centre → apparent longitude → obliquity → declination & equation of time → hour angle → elevation/azimuth, plus NOAA's refraction model for displayed elevation.
* **Events:** the geometric elevation is sampled every 5 minutes **across the local calendar day in the location's own IANA zone** (23 h or 25 h on DST days) and each threshold crossing is refined by bisection to < 0.25 s.
  * Sunrise/sunset: −0.833° · civil −6° · nautical −12° · astronomical −18°
  * Golden hour: +6° … −4° · blue hour: −4° … −6° (photography conventions, labelled as such in the UI)
  * Solar noon/midnight: zero crossings of the hour angle.
* This approach naturally handles polar day/night, days with a sunrise but no sunset, the International Date Line (e.g. Kiritimati UTC+14, Apia) and DST jumps.
* **Validation:** `SolarCalculatorTest` compares 280 location/date pairs with Skyfield + JPL DE421: mean error ≈ 1 s, max 13 s for sunrise/sunset. See [ACCURACY.md](ACCURACY.md).
* **Performance:** ~0.2 ms per day on the JVM; a whole year for the calendar chart takes < 100 ms. Results are memory-cached (LRU) and persisted in Room for month views.

## 5. Time zones

* Every saved place stores an **IANA zone id**; all formatting passes the zone explicitly (`TimeFormatter`). The phone's zone is only used to show "Your phone: …".
* Zone rules (DST, historical changes) come from `java.time`, which Android keeps up to date via the system tz data module.
* Coordinates → zone, fully offline: nearest of ~34 000 GeoNames cities (each with its IANA zone) within 400 km; otherwise the nautical zone `Etc/GMT±n`. Users can override the zone when entering coordinates (useful within a few km of a zone border).

## 6. Location & privacy

* Location is requested **only when the user taps a location button**, in the foreground, with `PRIORITY_BALANCED_POWER_ACCURACY` (works with approximate permission). A cached fix younger than 10 minutes is reused, so many refreshes cost no radio time at all.
* No background location, no continuous tracking. If permission is denied the app keeps working with saved places.
* The device fix is reverse-geocoded **offline** (nearest city); coordinates never leave the phone. Online search sends only the typed text.

## 7. Widget implementation strategy (and platform limits)

Home-screen widgets are `RemoteViews` hosted by the launcher: only a small set of views is allowed, custom `View`/`Canvas` drawing is impossible, `setTypeface` is not available, wallpaper blur is not available, and apps cannot update widgets every second.

DayArc therefore splits every widget into two layers:

1. **A bitmap "face"** drawn by `WidgetRenderer` with `android.graphics.Canvas`: background (solid, gradients, glass approximation, scenes, photos with crop/brightness/opacity, live sky), shape mask (rounded/pill/circle/oval/ring/bar/corner accent, anti-aliased), borders, arcs, rings, progress, icons and **all static text in any bundled font** with letter-spacing, case and shadows. This gives full design freedom and pixel-identical previews in Widget Studio (the editor renders with the same code).
2. **Native live views** positioned exactly over reserved "slots" of the face:
   * `TextClock` for the current time (per-city time zone, 12/24 h) — always exact, zero app wake-ups.
   * Optional `Chronometer` in count-down mode for "sunset in 01:23:45" — ticks every second inside the launcher.
   * Each bundled font has a tiny generated layout (`widget_live_<font>.xml`) so live text uses the same typeface (`android:fontFamily="@font/…"`).

Glance (`SizeMode.Exact`) provides the real widget size so the face is rendered at the actual dimensions; layouts adapt (e.g. 2×1 becomes a horizontal row, 1×1 shows icon + next event). Bitmaps are capped at ~750 k pixels to respect the RemoteViews memory budget.

**Updates** (`WidgetScheduler`):
* Event-boundary one-off work at the next sunrise, sunset, golden/blue-hour edge or local midnight of any widget's city.
* Periodic work (default 30 min, user-selectable 15–120 min; 15 min is Android's minimum) to move the sun along arcs and refresh "remaining" values.
* Immediate refresh on edits, location changes, time/zone changes and reboot (`SystemEventsReceiver`). WorkManager jobs persist across reboots.

**Independent widgets:** every placed widget (`appWidgetId`) owns a full copy of its `WidgetSpec` in `widget_instances`. Saved designs in Studio are templates; applying one to a widget copies it. Editing one widget never touches another. Android 12+ shows "Reconfigure" on long-press; older launchers open the editor when the widget is added.

**Pinning from the app:** `requestPinAppWidget` with a callback that binds the chosen design to the new `appWidgetId` (with a short-lived pending design as a fallback for launchers that update before the callback).

**Known limits (documented in-app):**
* Exact widget pixel sizes depend on the launcher; shapes like circles are drawn inside the rectangular cell with transparent corners.
* True blur of the wallpaper is not possible; glass is simulated.
* Values other than the clock/countdown refresh on events and at the chosen interval, not continuously.

## 8. Reminders

Only the **next** occurrence of each enabled reminder is scheduled with `AlarmManager` (exact if the user grants "Alarms & reminders", else a 10-minute window), computed in the reminder location's zone. After firing it schedules its next occurrence. Reboot, time and zone changes reschedule everything.

## 9. Branding

App name and application id live in `gradle.properties` (`suncanvas.appName`, `suncanvas.applicationId`); the name flows into `app_name` and `BuildConfig.BRAND_NAME`. See [BRANDING.md](BRANDING.md).
