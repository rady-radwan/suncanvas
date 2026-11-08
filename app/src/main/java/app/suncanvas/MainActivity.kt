package app.suncanvas

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.suncanvas.data.ThemeMode
import app.suncanvas.ui.AppViewModel
import app.suncanvas.ui.SunCanvasRoot
import app.suncanvas.ui.rememberNow
import app.suncanvas.ui.theme.SunCanvasTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        handleIntent(intent)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val active by vm.active.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val now = rememberNow(60_000L)
            val dark = when (settings?.themeMode ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SUN -> active?.let { !vm.solar.snapshot(it, now).isDay } ?: systemDark
            }
            SunCanvasTheme(darkTheme = dark) {
                SunCanvasRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_LOCATION_ID, -1L) ?: -1L
        if (id > 0) vm.setActive(id)
    }

    companion object {
        const val EXTRA_LOCATION_ID = "location_id"
        const val EXTRA_APP_WIDGET_ID = "app_widget_id"
    }
}
