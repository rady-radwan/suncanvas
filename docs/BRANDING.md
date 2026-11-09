# Renaming / rebranding DayArc

All brand values are in one place: **`gradle.properties`**

```properties
suncanvas.applicationId=com.dayarc.app     # Play Store / install identity
suncanvas.appName=DayArc              # launcher label, onboarding, About screen
suncanvas.versionCode=1
suncanvas.versionName=0.1.0
```

* `appName` generates the `app_name` string resource and `BuildConfig.BRAND_NAME` (used in the UI). Debug builds show "<name> Debug" and install side by side (`.debug` suffix).
* Changing `applicationId` creates a separate app on the device; do it before publishing.
* The Kotlin package (`app.suncanvas`) is internal and does not need to change.

## Logo

* Source: `branding/suncanvas-icon.svg` (rounded) and `branding/suncanvas-icon-fullbleed.svg` (square, for stores).
* Android adaptive icon layers: `app/src/main/res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` (themed icons on Android 13+). Keep artwork inside the central 66 dp circle of the 108 dp canvas.
* In-app mark: `drawable/logo_mark.xml`. Notification icon: `drawable/ic_stat_sun.xml` (white silhouette).
* To use a new logo, replace those vector files (Android Studio → New → Vector Asset can convert an SVG) and re-export `branding/suncanvas-icon-512.png` for the store listing.
