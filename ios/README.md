# DayArc for iPhone

Native SwiftUI + WidgetKit version of DayArc: offline sunrise/sunset (NOAA), My Cities, World Clock,
Calendar with charts, reminders, automatic sunrise/sunset theme, backup/restore (compatible with the
Android app) and the Widget Studio with Home Screen (small / medium / large) and Lock Screen widgets.

Requires iOS 17 or later.

## Install on your iPhone from a Windows PC (Sideloadly)

1. **Download the app file.** Open the repository's Releases page → **ios-latest** → download `DayArc.ipa`
   (or take it from the `ios-ipa` branch).
2. **Install the tools on Windows.**
   - Install iTunes and iCloud *from apple.com* (not the Microsoft Store versions) so Windows can talk to the iPhone.
   - Download and install Sideloadly from sideloadly.io.
3. **Connect the iPhone** with a USB cable, unlock it and tap **Trust This Computer**.
4. **Sideload.** Open Sideloadly, drag `DayArc.ipa` into the window, choose your iPhone, enter your Apple ID and
   click **Start**. (A free Apple ID works. Sideloadly asks for the password to sign the app; consider an
   app-specific Apple ID if you prefer.)
5. **Turn on Developer Mode** (first time only): iPhone **Settings → Privacy & Security → Developer Mode → On**, restart, confirm.
6. **Trust the developer profile**: **Settings → General → VPN & Device Management →** your Apple ID → **Trust**.
7. Open **DayArc**, choose your location or a city.

**Free Apple ID note:** apps signed with a free account expire after **7 days**. Just run Sideloadly again with the same
IPA (your data stays). Sideloadly's "Auto refresh" option can do this for you over Wi-Fi.

## Add the widgets

- **Home Screen:** touch and hold the Home Screen → **Edit → Add Widget** → **DayArc** → pick Small, Medium or Large →
  **Add Widget**. Then touch and hold the widget → **Edit Widget** → choose a **Design** (your saved Widget Studio
  designs and all 22 templates) and optionally a **City**. Add as many as you like; each has its own design and city.
- **Lock Screen:** touch and hold the Lock Screen → **Customize → Lock Screen** → tap the widget area → **DayArc**
  (circular, rectangular, inline).

If a widget only lists templates and not your own designs, Sideloadly couldn't keep the App Group that lets the app and
widget share data. Widgets still work with templates and with any city you pick in Edit Widget.

## Build

CI (`.github/workflows/ios.yml`, macOS runner) runs the DayArcCore unit tests, generates the Xcode project with
XcodeGen, builds an unsigned Release build, ad-hoc signs it with its entitlements, and publishes `DayArc.ipa`.
Sideloadly re-signs it with your Apple ID.

Local build on a Mac:

```sh
brew install xcodegen
cd ios && xcodegen generate && open DayArc.xcodeproj
(cd DayArcCore && swift test)
```

## Layout

- `DayArcCore/` – Swift package shared by the app and the widget: NOAA solar engine, models (same JSON as Android),
  22 templates and 12 palettes, offline city database (34,000 cities), shared storage, backup, and `WidgetFace`, the
  SwiftUI view that renders every widget and every Studio preview.
- `App/` – the app screens.
- `Widget/` – WidgetKit extension with App Intent configuration (design + city) and Lock Screen widgets.
