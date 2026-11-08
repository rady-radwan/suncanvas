package app.suncanvas.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.suncanvas.container
import app.suncanvas.ui.screens.widgets.WidgetEditorScreen
import app.suncanvas.ui.theme.SunCanvasTheme
import app.suncanvas.widget.model.WidgetTemplates
import kotlinx.coroutines.launch

/**
 * Opened by the launcher when a widget is added (Android 11 and lower) or when the user chooses
 * "Reconfigure" / taps the widget's settings (Android 12+). Edits only this widget's own spec.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // If the user backs out, the launcher should still keep (or not add) the widget as per platform rules.
        setResult(Activity.RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        setContent {
            var ready by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                val loader = container.widgetLoader
                container.widgets.getOrCreateInstance(appWidgetId, WidgetTemplates.defaultFor(loader.sizeClassFor(appWidgetId)))
                ready = true
            }
            SunCanvasTheme(darkTheme = isSystemInDarkTheme()) {
                if (ready) {
                    WidgetEditorScreen(
                        designId = null,
                        appWidgetId = appWidgetId,
                        templateId = null,
                        onClose = { finishWithOk() },
                        onSavedToWidget = { finishWithOk() },
                    )
                }
            }
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    private fun finishWithOk() {
        lifecycleScope.launch {
            WidgetUpdater.update(this@WidgetConfigActivity, appWidgetId)
            WidgetScheduler.ensureScheduled(this@WidgetConfigActivity)
            setResult(Activity.RESULT_OK, resultIntent())
            finish()
        }
    }
}
