package app.suncanvas

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.suncanvas.data.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Smoke test + screenshots of the main screens (written to app/build/screens for review).
 * Launches the real activity with seeded data and walks through every tab.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun seed(theme: ThemeMode) = runBlocking {
        val c = context.container
        c.settings.setOnboardingDone(true)
        c.settings.setTheme(theme)
        if (c.locations.getAll().isEmpty()) {
            val dallas = c.locations.addManual("Dallas", 32.7767, -96.7970, "America/Chicago", "Texas", "US", "United States", makeDefault = true, makeActive = true)
            c.locations.addManual("Cairo", 30.0444, 31.2357, "Africa/Cairo", "", "EG", "Egypt")
            c.locations.addManual("London", 51.5074, -0.1278, "Europe/London", "England", "GB", "United Kingdom")
            c.locations.addManual("Tokyo", 35.6762, 139.6503, "Asia/Tokyo", "", "JP", "Japan")
            c.locations.setActive(dallas)
        }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/$name.png")
    }

    private fun tap(text: String) {
        compose.onAllNodesWithText(text)[0].performClick()
        compose.waitForIdle()
    }

    @Test
    fun walkThroughAllTabsLight() {
        seed(ThemeMode.LIGHT)
        ActivityScenario.launch(MainActivity::class.java).use {
            shot("01_home_light")
            tap("Cities"); shot("02_cities")
            tap("World Clock"); shot("03_world_clock")
            tap("Calendar"); shot("04_calendar_day")
            tap("Month"); shot("05_calendar_month")
            tap("Year"); shot("06_calendar_year")
            tap("Widgets"); shot("07_widget_studio")
            tap("Settings"); shot("08_settings")
        }
    }

    @Test
    fun homeAndEditorDark() {
        seed(ThemeMode.DARK)
        ActivityScenario.launch(MainActivity::class.java).use {
            shot("10_home_dark")
            tap("Widgets")
            tap("Digital Clock + Sun")
            shot("11_editor_design")
            tap("Background"); shot("12_editor_background")
            tap("Text"); shot("13_editor_text")
        }
    }
}
