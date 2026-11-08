package app.suncanvas.ui.screens.widgets

import android.app.Application
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.suncanvas.container
import app.suncanvas.widget.WidgetPinnedReceiver
import app.suncanvas.widget.WidgetScheduler
import app.suncanvas.widget.WidgetUpdater
import app.suncanvas.widget.model.BackgroundType
import app.suncanvas.widget.model.WidgetSizeClass
import app.suncanvas.widget.model.WidgetSpec
import app.suncanvas.widget.model.WidgetTemplates
import app.suncanvas.widget.render.WidgetData
import app.suncanvas.widget.render.WidgetPainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.math.max

data class EditorState(
    val spec: WidgetSpec,
    val original: WidgetSpec,
    val designId: Long?,
    val appWidgetId: Int?,
    val data: WidgetData?,
    val dirty: Boolean = false,
    val loading: Boolean = true,
)

class WidgetEditorViewModel(
    app: Application,
    private val designId: Long?,
    private val appWidgetId: Int?,
    private val templateId: String?,
) : AndroidViewModel(app) {

    private val c = app.container
    private val initial = WidgetTemplates.byId(templateId ?: "")?.spec ?: WidgetTemplates.default.spec
    private val _state = MutableStateFlow(EditorState(initial, initial, designId, appWidgetId, null))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val spec = when {
                appWidgetId != null -> c.widgets.instance(appWidgetId)?.specJson?.let { WidgetSpec.fromJson(it) }
                designId != null -> c.widgets.design(designId)?.specJson?.let { WidgetSpec.fromJson(it) }
                else -> null
            } ?: initial
            _state.update { it.copy(spec = spec, original = spec, loading = false) }
            reloadData()
        }
    }

    fun edit(transform: (WidgetSpec) -> WidgetSpec) {
        val before = _state.value.spec
        val after = transform(before)
        _state.update { it.copy(spec = after, dirty = true) }
        if (after.locationId != before.locationId || after.extraLocationIds != before.extraLocationIds) reloadData()
    }

    fun applyTemplate(id: String) {
        val t = WidgetTemplates.byId(id) ?: return
        edit { old -> t.spec.copy(locationId = old.locationId, extraLocationIds = old.extraLocationIds) }
    }

    fun reset() {
        _state.update { it.copy(spec = it.original, dirty = false) }
        reloadData()
    }

    fun reloadData() {
        viewModelScope.launch {
            val d = c.widgetLoader.dataFor(_state.value.spec, Instant.now())
            _state.update { it.copy(data = d) }
        }
    }

    /** Saves to the placed widget (if editing one) or to the saved design. */
    fun save(onDone: (String) -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            if (s.appWidgetId != null) {
                c.widgets.saveInstance(s.appWidgetId, s.spec)
                WidgetUpdater.update(getApplication(), s.appWidgetId)
                WidgetScheduler.scheduleNextBoundary(getApplication())
                _state.update { it.copy(original = it.spec, dirty = false) }
                onDone("Widget updated")
            } else {
                val id = c.widgets.saveDesign(s.spec, s.designId)
                _state.update { it.copy(designId = id, original = it.spec, dirty = false) }
                onDone("Design saved")
            }
        }
    }

    fun saveAsNew(onDone: (String) -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            val id = c.widgets.saveDesign(s.spec.copy(name = s.spec.name + " copy"), null)
            onDone("Saved as a new design")
            _state.update { it.copy(designId = if (it.appWidgetId == null) id else it.designId) }
        }
    }

    fun preferredSizeClass(): WidgetSizeClass =
        WidgetTemplates.byId(_state.value.spec.templateId)?.sizeClass ?: WidgetSizeClass.WIDE

    /** Asks the launcher to pin a new widget that adopts the current design. */
    fun pinToHome(sizeClass: WidgetSizeClass, onResult: (String) -> Unit) {
        val context = getApplication<Application>()
        val mgr = AppWidgetManager.getInstance(context)
        if (!mgr.isRequestPinAppWidgetSupported) {
            onResult("Your launcher doesn't support adding widgets from apps. Long-press the home screen → Widgets → SunCanvas.")
            return
        }
        viewModelScope.launch {
            val s = _state.value
            val json = s.spec.toJson()
            c.settings.setPendingPin(json, s.designId)
            val provider = ComponentName(context, WidgetUpdater.receiverFor(sizeClass))
            val callback = PendingIntent.getBroadcast(
                context, 7001,
                Intent(context, WidgetPinnedReceiver::class.java)
                    .putExtra(WidgetPinnedReceiver.EXTRA_SPEC_JSON, json)
                    .putExtra(WidgetPinnedReceiver.EXTRA_DESIGN_ID, s.designId ?: -1L),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            val ok = mgr.requestPinAppWidget(provider, Bundle(), callback)
            onResult(if (ok) "Choose where to place the widget" else "Couldn't request the widget. Add it from the home screen widget picker.")
        }
    }

    /** Copies a picked photo into private storage (downscaled) and uses it as the background. */
    fun importImage(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val ctx = getApplication<Application>()
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    var sample = 1
                    while (max(opts.outWidth, opts.outHeight) / (sample * 2) >= 1600) sample *= 2
                    val bmp = ctx.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: return@runCatching null
                    val dir = File(ctx.filesDir, "widget_backgrounds").apply { mkdirs() }
                    val file = File(dir, "${UUID.randomUUID()}.jpg")
                    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                    file.absolutePath
                }.getOrNull()
            }
            if (path != null) edit { it.copy(background = it.background.copy(type = BackgroundType.IMAGE, imagePath = path)) }
        }
    }

    companion object {
        fun factory(app: Application, designId: Long?, appWidgetId: Int?, templateId: String?) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = WidgetEditorViewModel(app, designId, appWidgetId, templateId) as T
        }

        /** Rough estimate of the colour behind text, composited over a light or dark wallpaper. */
        fun approximateBackground(spec: WidgetSpec, wallpaper: Int): Int {
            val bg = spec.background
            val base = when (bg.type) {
                BackgroundType.SOLID -> bg.color
                BackgroundType.GRADIENT -> mix(bg.color, bg.color2)
                BackgroundType.MULTI_GRADIENT -> bg.colors.fold(bg.colors.firstOrNull() ?: bg.color) { a, b -> mix(a, b) }
                BackgroundType.TRANSPARENT -> return wallpaper
                BackgroundType.GLASS_DARK -> 0xFF141A38.toInt()
                BackgroundType.GLASS_LIGHT -> 0xFFE9ECF5.toInt()
                BackgroundType.SUNRISE -> 0xFF8A5A8A.toInt()
                BackgroundType.SUNSET -> 0xFF8E3B6A.toInt()
                BackgroundType.NIGHT_SKY -> 0xFF0B1026.toInt()
                BackgroundType.SCENE -> when (bg.scene) {
                    app.suncanvas.widget.model.SceneId.DUNES, app.suncanvas.widget.model.SceneId.PAPER -> 0xFFEAD2B0.toInt()
                    app.suncanvas.widget.model.SceneId.OCEAN -> 0xFF1B5F9E.toInt()
                    else -> 0xFF2A2350.toInt()
                }
                BackgroundType.IMAGE -> WidgetPainter.over(WidgetPainter.withAlpha(bg.overlayColor, bg.overlayOpacity), 0xFF808080.toInt())
                BackgroundType.LIVE_SKY -> 0xFF3A4A8A.toInt()
            }
            val glassAlpha = when (bg.type) { BackgroundType.GLASS_DARK -> 0.85f; BackgroundType.GLASS_LIGHT -> 0.6f; else -> 1f }
            return WidgetPainter.over(WidgetPainter.withAlpha(base or 0xFF000000.toInt(), bg.opacity * glassAlpha), wallpaper)
        }

        private fun mix(a: Int, b: Int): Int = android.graphics.Color.rgb(
            (android.graphics.Color.red(a) + android.graphics.Color.red(b)) / 2,
            (android.graphics.Color.green(a) + android.graphics.Color.green(b)) / 2,
            (android.graphics.Color.blue(a) + android.graphics.Color.blue(b)) / 2,
        )

        /** Lowest text contrast ratio across a light and a dark wallpaper. */
        fun worstContrast(spec: WidgetSpec): Double {
            val walls = listOf(0xFFF2F2F2.toInt(), 0xFF202124.toInt())
            return walls.minOf { wall ->
                val bg = approximateBackground(spec, wall)
                val text = WidgetPainter.over(WidgetPainter.withAlpha(spec.colors.text, spec.textOpacity), bg)
                WidgetPainter.contrast(text, bg)
            }
        }
    }
}
