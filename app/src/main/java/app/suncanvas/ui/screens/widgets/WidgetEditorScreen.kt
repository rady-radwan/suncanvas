package app.suncanvas.ui.screens.widgets

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.suncanvas.container
import app.suncanvas.ui.components.ColorSwatchButton
import app.suncanvas.ui.components.SectionHeader
import app.suncanvas.ui.theme.Spacing
import app.suncanvas.widget.model.Align
import app.suncanvas.widget.model.BackgroundType
import app.suncanvas.widget.model.FontWeightChoice
import app.suncanvas.widget.model.SceneId
import app.suncanvas.widget.model.TextCase
import app.suncanvas.widget.model.WidgetElement
import app.suncanvas.widget.model.WidgetFont
import app.suncanvas.widget.model.WidgetLayout
import app.suncanvas.widget.model.WidgetPalettes
import app.suncanvas.widget.model.WidgetShape
import app.suncanvas.widget.model.WidgetSizeClass
import app.suncanvas.widget.model.WidgetSpec
import app.suncanvas.widget.model.WidgetTemplates
import app.suncanvas.widget.render.FontResources
import kotlinx.coroutines.launch

private val SIZES = listOf(1 to 1, 2 to 1, 2 to 2, 3 to 1, 3 to 2, 4 to 1, 4 to 2, 4 to 3, 4 to 4)
private val SECTIONS = listOf("Design", "Content", "Location", "Shape", "Background", "Colors", "Text", "Effects")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WidgetEditorScreen(designId: Long?, appWidgetId: Int?, templateId: String?, onClose: () -> Unit, onSavedToWidget: (() -> Unit)? = null) {
    val app = LocalContext.current.applicationContext as Application
    val vm: WidgetEditorViewModel = viewModel(
        key = "editor-$designId-$appWidgetId-$templateId",
        factory = WidgetEditorViewModel.factory(app, designId, appWidgetId, templateId),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val spec = state.spec
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableIntStateOf(0) }
    val defaultSize = WidgetTemplates.byId(spec.templateId)?.sizeClass ?: WidgetSizeClass.WIDE
    var size by rememberSaveable(spec.templateId) { mutableStateOf(defaultSize.cellsW to defaultSize.cellsH) }
    var wallpaper by rememberSaveable { mutableStateOf(SampleWallpaper.MOUNTAINS) }
    var renaming by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    fun toast(msg: String) { scope.launch { snackbar.showSnackbar(msg) } }

    androidx.compose.material3.Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(Modifier.clickable { renaming = true }, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (appWidgetId != null) "Edit widget" else spec.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Rounded.Edit, "Rename", Modifier.padding(start = 6.dp).size(16.dp))
                    }
                },
                navigationIcon = { IconButton(onClick = { if (state.dirty) confirmLeave = true else onClose() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { vm.reset() }, enabled = state.dirty) { Icon(Icons.Rounded.RestartAlt, "Reset changes") }
                    IconButton(onClick = { vm.save { toast(it); if (appWidgetId != null) onSavedToWidget?.invoke() } }) { Icon(Icons.Rounded.Save, "Save") }
                },
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding().padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (appWidgetId == null) {
                    OutlinedButton(onClick = { vm.saveAsNew { toast(it) } }, modifier = Modifier.weight(1f)) { Text("Save as new") }
                    Button(onClick = {
                        val sc = sizeClassFor(size)
                        vm.save { }
                        vm.pinToHome(sc) { toast(it) }
                    }, modifier = Modifier.weight(1.4f)) {
                        Icon(Icons.AutoMirrored.Rounded.AddToHomeScreen, null, Modifier.size(18.dp)); Text(" Add to home screen")
                    }
                } else {
                    Button(onClick = { vm.save { toast(it); onSavedToWidget?.invoke() } }, modifier = Modifier.fillMaxWidth()) { Text("Apply to this widget") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ---------------------------------------------------------------- live preview
            BoxWithConstraints(Modifier.fillMaxWidth().height(250.dp)) {
                WallpaperBackground(wallpaper, Modifier.fillMaxSize())
                val (nw, nh) = nominalSize(size.first, size.second)
                val scale = minOf((maxWidth.value - 24) / nw.value, (maxHeight.value - 24) / nh.value, 1f)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WidgetPreview(spec, state.data, nw, nh, displayScale = scale)
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.md, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SIZES.forEach { s ->
                    FilterChip(selected = size == s, onClick = { size = s }, label = { Text("${s.first}×${s.second}") })
                }
                Spacer(Modifier.width(Spacing.sm))
                SampleWallpaper.entries.forEach { w ->
                    FilterChip(selected = wallpaper == w, onClick = { wallpaper = w }, label = { Text(w.label) })
                }
            }
            val contrast = remember(spec) { WidgetEditorViewModel.worstContrast(spec) }
            if (contrast < 3.0) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.md).clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.errorContainer).padding(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        " Low contrast (%.1f:1): text may be hard to read on some wallpapers. Raise background opacity or change the text color.".format(contrast),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            ScrollableTabRow(selectedTabIndex = section, edgePadding = Spacing.md) {
                SECTIONS.forEachIndexed { i, s -> Tab(selected = section == i, onClick = { section = i }, text = { Text(s) }) }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                when (SECTIONS[section]) {
                    "Design" -> DesignSection(spec, vm)
                    "Content" -> ContentSection(spec, vm)
                    "Location" -> LocationSection(spec, vm)
                    "Shape" -> ShapeSection(spec, vm)
                    "Background" -> BackgroundSection(spec, vm)
                    "Colors" -> ColorsSection(spec, vm)
                    "Text" -> TextSection(spec, vm)
                    "Effects" -> EffectsSection(spec, vm)
                }
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(spec.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Design name") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.edit { it.copy(name = name.ifBlank { it.name }) }; renaming = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes to this design.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onClose() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false; vm.save { onClose() } }) { Text("Save") } },
        )
    }
}

private fun sizeClassFor(size: Pair<Int, Int>): WidgetSizeClass = when {
    size.first >= 4 && size.second >= 3 -> WidgetSizeClass.LARGE
    size.first >= 3 -> WidgetSizeClass.WIDE
    size.second >= 2 -> WidgetSizeClass.SQUARE
    else -> WidgetSizeClass.SMALL
}

// ------------------------------------------------------------------ building blocks

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

private fun pct(v: Float) = "${(v * 100).toInt()}%"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGroup(options: List<T>, selected: (T) -> Boolean, label: (T) -> String, onClick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o -> FilterChip(selected = selected(o), onClick = { onClick(o) }, label = { Text(label(o)) }) }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, supporting: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

// ------------------------------------------------------------------ sections

@Composable
private fun DesignSection(spec: WidgetSpec, vm: WidgetEditorViewModel) {
    SectionHeader("Start from a template")
    ChipGroup(WidgetTemplates.all, { it.id == spec.templateId }, { it.name }) { vm.applyTemplate(it.id) }
    SectionHeader("Layout")
    ChipGroup(WidgetLayout.entries, { it == spec.layout }, { it.label }) { l -> vm.edit { it.copy(layout = l) } }
    SectionHeader("Palette")
    ChipGroup(WidgetPalettes.all, { it.colors == spec.colors }, { it.name }) { p -> vm.edit { WidgetPalettes.apply(it, p) } }
}

@Composable
private fun ContentSection(spec: WidgetSpec, vm: WidgetEditorViewModel) {
    SectionHeader("Show on the widget")
    ChipGroup(WidgetElement.entries, { it in spec.elements }, { it.label }) { e ->
        vm.edit { it.copy(elements = if (e in it.elements) it.elements - e else it.elements + e) }
    }
    Text("Each layout uses the elements that fit its size; hidden items never overlap.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
    SectionHeader("Alignment")
    ChipGroup(Align.entries, { it == spec.align }, { it.label }) { a -> vm.edit { it.copy(align = a) } }
    SectionHeader("Spacing")
    SliderRow("Inner padding", spec.padding, 0f..32f, "${spec.padding.toInt()} dp") { v -> vm.edit { it.copy(padding = v) } }
    SliderRow("Row spacing", spec.spacing, 0.4f..2f, "%.1f×".format(spec.spacing)) { v -> vm.edit { it.copy(spacing = v) } }
}

@Composable
private fun LocationSection(spec: WidgetSpec, vm: WidgetEditorViewModel) {
    val c = LocalContext.current.container
    val all by c.locations.all.collectAsStateWithLifecycle(initialValue = emptyList())
    SectionHeader("City for this widget")
    Column {
        LocationOption("Follow my default location", spec.locationId == null) { vm.edit { it.copy(locationId = null) } }
        all.forEach { l -> LocationOption(l.headline, spec.locationId == l.id) { vm.edit { it.copy(locationId = l.id) } } }
    }
    if (spec.layout == WidgetLayout.MULTI_CITY) {
        SectionHeader("Other cities (multi-city layouts)")
        Text("None selected = your favorites and saved cities.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipGroup(all, { it.id in spec.extraLocationIds }, { it.displayName }) { l ->
            vm.edit { s -> s.copy(extraLocationIds = if (l.id in s.extraLocationIds) s.extraLocationIds - l.id else s.extraLocationIds + l.id) }
        }
    }
}

@Composable
private fun LocationOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = Spacing.sm))
    }
}

@Composable
private fun ShapeSection(spec: WidgetSpec, vm: WidgetEditorViewModel) {
    SectionHeader("Shape")
    ChipGroup(WidgetShape.entries, { it == spec.shape }, { it.label }) { s -> vm.edit { it.copy(shape = s) } }
    if (spec.shape in setOf(WidgetShape.ROUNDED, WidgetShape.CUSTOM, WidgetShape.CORNER_ACCENT, WidgetShape.BORDERLESS)) {
        SliderRow("Corner radius", spec.cornerRadius, 0f..80f, "${spec.cornerRadius.toInt()} dp") { v -> vm.edit { it.copy(cornerRadius = v, shape = if (it.shape == WidgetShape.ROUNDED) WidgetShape.CUSTOM else it.shape) } }
    }
    SectionHeader("Border")
    SliderRow("Width", spec.border.width, 0f..6f, "%.1f dp".format(spec.border.width)) { v -> vm.edit { it.copy(border = it.border.copy(width = v)) } }
    SliderRow("Opacity", spec.border.opacity, 0f..1f, pct(spec.border.opacity)) { v -> vm.edit { it.copy(border = it.border.copy(opacity = v)) } }
    ColorSwatchButton("Border color", spec.border.color, { col -> vm.edit { it.copy(border = it.border.copy(color = col)) } })
    Text(
        "Android widgets are rectangles placed by your launcher; circles, pills and rings are drawn inside that rectangle with transparent corners.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundSection(spec: WidgetSpec, vm: WidgetEditorViewModel) {
    val bg = spec.background
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.importImage(uri) }
    SectionHeader("Type")
    ChipGroup(BackgroundType.entries, { it == bg.type }, { it.label }) { t ->
        if (t == BackgroundType.IMAGE && bg.imagePath == null) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        else vm.edit { it.copy(background = it.background.copy(type = t, opacity = if (t == BackgroundType.TRANSPARENT) 0f else it.background.opacity.coerceAtLeast(0.05f))) }
    }
    SliderRow("Background opacity", bg.opacity, 0f..1f, pct(bg.opacity)) { v -> vm.edit { it.copy(background = it.background.copy(opacity = v)) } }
    when (bg.type) {
        BackgroundType.SOLID -> ColorSwatchButton("Color", bg.color, { col -> vm.edit { it.copy(background = it.background.copy(color = col)) } })
        BackgroundType.GRADIENT -> {
            ColorSwatchButton("Start color", bg.color, { col -> vm.edit { it.copy(background = it.background.copy(color = col)) } })
            ColorSwatchButton("End color", bg.color2, { col -> vm.edit { it.copy(background = it.background.copy(color2 = col)) } })
            SliderRow("Angle", bg.angle, 0f..360f, "${bg.angle.toInt()}°") { v -> vm.edit { it.copy(background = it.background.copy(angle = v)) } }
        }
        BackgroundType.MULTI_GRADIENT -> {
            bg.colors.forEachIndexed { i, col ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorSwatchButton("Color ${i + 1}", col, { nc -> vm.edit { s -> s.copy(background = s.background.copy(colors = s.background.colors.toMutableList().also { l -> l[i] = nc })) } }, Modifier.weight(1f))
                    if (bg.colors.size > 2) TextButton(onClick = { vm.edit { s -> s.copy(background = s.background.copy(colors = s.background.colors.filterIndexed { j, _ -> j != i })) } }) { Text("Remove") }
                }
            }
            if (bg.colors.size < 6) TextButton(onClick = { vm.edit { s -> s.copy(background = s.background.copy(colors = s.background.colors + s.background.colors.last())) } }) { Text("Add color") }
            SliderRow("Angle", bg.angle, 0f..360f, "${bg.angle.toInt()}°") { v -> vm.edit { it.copy(background = it.background.copy(angle = v)) } }
        }
        BackgroundType.SCENE -> {
            SectionHeader("Built-in backgrounds")
            ChipGroup(SceneId.entries, { it == bg.scene }, { it.label }) { sc -> vm.edit { it.copy(background = it.background.copy(scene = sc)) } }
        }
        BackgroundType.IMAGE -> {
            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Rounded.Image, null); Text(" Choose photo")
            }
            SliderRow("Zoom", bg.imageZoom, 1f..4f, "%.1f×".format(bg.imageZoom)) { v -> vm.edit { it.copy(background = it.background.copy(imageZoom = v)) } }
            SliderRow("Horizontal crop", bg.imageOffsetX, -1f..1f, "%.2f".format(bg.imageOffsetX)) { v -> vm.edit { it.copy(background = it.background.copy(imageOffsetX = v)) } }
            SliderRow("Vertical crop", bg.imageOffsetY, -1f..1f, "%.2f".format(bg.imageOffsetY)) { v -> vm.edit { it.copy(background = it.background.copy(imageOffsetY = v)) } }
            SliderRow("Brightness", bg.imageBrightness, -1f..1f, "%+d".format((bg.imageBrightness * 100).toInt())) { v -> vm.edit { it.copy(background = it.background.copy(imageBrightness = v)) } }
            SliderRow("Photo opacity", bg.imageOpacity, 0f..1f, pct(bg.imageOpacity)) { v -> vm.edit { it.copy(background = it.background.copy(imageOpacity = v)) } }
            SliderRow("Tint overlay", bg.overlayOpacity, 0f..0.9f, pct(bg.overlayOpacity)) { v -> vm.edit { it.copy(background = it.background.copy(overlayOpacity = v)) } }
            ColorSwatchButton("Tint color", bg.overlayColor, { col -> vm.edit { it.copy(background = it.background.copy(overlayColor = col)) } })
        }
        BackgroundType.GLASS_DARK, BackgroundType.GLASS_LIGHT -> Text(
            "Home-screen widgets can't blur the wallpaper behind them, so glass is simulated with a translucent tint and highlights.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BackgroundType.LIVE_SKY -> Text("Changes through the day with the Sun's elevation: blue sky, golden light, twilight and stars.", style = MaterialTheme.typography.bodySmall)
        else -> Unit
    }
}

@Composable
private fun ColorsSection(spec: WidgetSpec, vm: WidgetEditorViewModel) {
    SectionHeader("Palettes")
    ChipGroup(WidgetPalettes.all, { it.colors == spec.colors }, { it.name }) { p -> vm.edit { WidgetPalettes.apply(it, p) } }
    SectionHeader("Custom colors")
    val col = spec.colors
    ColorSwatchButton("Text", col.text, { v -> vm.edit { it.copy(colors = it.colors.copy(text = v)) } })
    ColorSwatchButton("Labels & secondary text", col.secondaryText, { v -> vm.edit { it.copy(colors = it.colors.copy(secondaryText = v)) } })
    ColorSwatchButton("Sunrise", col.sunrise, { v -> vm.edit { it.copy(colors = it.colors.copy(sunrise = v)) } })
    ColorSwatchButton("Sunset", col.sunset, { v -> vm.edit { it.copy(colors = it.colors.copy(sunset = v)) } })
    ColorSwatchButton("Arc & sun", col.arc, { v -> vm.edit { it.copy(colors = it.colors.copy(arc = v)) } })
    ColorSwatchButton("Icons", col.icon, { v -> vm.edit { it.copy(colors = it.colors.copy(icon = v)) } })
    ColorSwatchButton("Accent", col.accent, { v -> vm.edit { it.copy(colors = it.colors.copy(accent = v)) } })
    ColorSwatchButton("Background", spec.background.color, { v -> vm.edit { it.copy(background = it.background.copy(color = v)) } })
    ColorSwatchButton("Border", spec.border.color, { v -> vm.edit { it.copy(border = it.border.copy(color = v)) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextSection(spec: WidgetSpec, vm: WidgetEditorViewModel) {
    val t = spec.typography
    SectionHeader("Font")
    FontGrid(t.font) { f -> vm.edit { it.copy(typography = it.typography.copy(font = f)) } }
    SectionHeader("Weight")
    ChipGroup(FontWeightChoice.entries, { it == t.weight }, { it.label }) { w -> vm.edit { it.copy(typography = it.typography.copy(weight = w)) } }
    SectionHeader("Clock font")
    FontGrid(t.clockFont ?: t.font) { f -> vm.edit { it.copy(typography = it.typography.copy(clockFont = f)) } }
    ChipGroup(FontWeightChoice.entries, { it == t.clockWeight }, { it.label }) { w -> vm.edit { it.copy(typography = it.typography.copy(clockWeight = w)) } }
    SectionHeader("Sizes")
    SliderRow("Text size", t.scale, 0.6f..1.6f, pct(t.scale)) { v -> vm.edit { it.copy(typography = it.typography.copy(scale = v)) } }
    SliderRow("Clock size", t.clockScale, 0.5f..1.5f, pct(t.clockScale)) { v -> vm.edit { it.copy(typography = it.typography.copy(clockScale = v)) } }
    SliderRow("Sunrise/sunset label size", t.labelScale, 0.6f..1.6f, pct(t.labelScale)) { v -> vm.edit { it.copy(typography = it.typography.copy(labelScale = v)) } }
    SliderRow("Letter spacing", t.letterSpacing, -0.05f..0.4f, "%.2f em".format(t.letterSpacing)) { v -> vm.edit { it.copy(typography = it.typography.copy(letterSpacing = v)) } }
    SectionHeader("Case")
    ChipGroup(TextCase.entries, { it == t.case }, { it.label }) { cs -> vm.edit { it.copy(typography = it.typography.copy(case = cs)) } }
    Text("Labels", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Spacing.sm))
    ChipGroup(TextCase.entries, { it == t.labelCase }, { it.label }) { cs -> vm.edit { it.copy(typography = it.typography.copy(labelCase = cs)) } }
    SwitchRow("Soft text shadow", t.shadow, "Improves legibility over busy wallpapers") { on -> vm.edit { it.copy(typography = it.typography.copy(shadow = on)) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontGrid(selected: WidgetFont, onPick: (WidgetFont) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WidgetFont.entries.forEach { f ->
            val family = remember(f) { FontFamily(Font(FontResources.fontRes(f, FontWeightChoice.REGULAR))) }
            val isSel = f == selected
            Column(
                Modifier.width(98.dp).clip(MaterialTheme.shapes.small)
                    .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                    .border(if (isSel) 1.5.dp else 0.dp, if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent, MaterialTheme.shapes.small)
                    .clickable { onPick(f) }.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("6:58", fontFamily = family, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text(f.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EffectsSection(spec: WidgetSpec, vm: WidgetEditorViewModel) {
    SectionHeader("Opacity")
    SliderRow("Text opacity", spec.textOpacity, 0.3f..1f, pct(spec.textOpacity)) { v -> vm.edit { it.copy(textOpacity = v) } }
    SliderRow("Decorative overlays", spec.decorOpacity, 0f..1f, pct(spec.decorOpacity)) { v -> vm.edit { it.copy(decorOpacity = v) } }
    SectionHeader("Live elements")
    SwitchRow("Live clock", spec.liveClock, "Uses Android's built-in clock view: always exact, zero battery cost") { on -> vm.edit { it.copy(liveClock = on) } }
    SwitchRow("Live countdown (seconds)", spec.liveCountdown, "Ticking countdown via Android's Chronometer. Off = minute value refreshed in the background") { on -> vm.edit { it.copy(liveCountdown = on) } }
    HorizontalDivider(Modifier.padding(vertical = Spacing.md))
    Text(
        "Sun position, progress and \"remaining\" values update at every sunrise, sunset, golden-hour edge and midnight, and at the background refresh interval set in Settings.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
