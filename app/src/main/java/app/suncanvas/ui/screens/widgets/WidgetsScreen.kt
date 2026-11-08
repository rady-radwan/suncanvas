package app.suncanvas.ui.screens.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.suncanvas.container
import app.suncanvas.ui.components.SectionHeader
import app.suncanvas.ui.components.SunCard
import app.suncanvas.ui.theme.Spacing
import app.suncanvas.widget.WidgetUpdater
import app.suncanvas.widget.model.WidgetSizeClass
import app.suncanvas.widget.model.WidgetSpec
import app.suncanvas.widget.model.WidgetTemplates
import app.suncanvas.widget.render.WidgetData
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsScreen(onNewFromTemplate: (String) -> Unit, onEditDesign: (Long) -> Unit, onEditWidget: (Int) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val designs by c.widgets.designs.collectAsStateWithLifecycle(initialValue = emptyList())
    val instances by c.widgets.instances.collectAsStateWithLifecycle(initialValue = emptyList())
    val locations by c.locations.all.collectAsStateWithLifecycle(initialValue = emptyList())
    var data by remember { mutableStateOf<WidgetData?>(null) }
    val scope = rememberCoroutineScope()
    val placedIds = remember(instances) { WidgetUpdater.allAppWidgetIds(context).toSet() }
    LaunchedEffect(locations) { data = c.widgetLoader.dataFor(WidgetTemplates.default.spec) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Widget Studio") })
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xxl)) {
            val placed = instances.filter { it.appWidgetId in placedIds }
            item { SectionHeader("On your home screen") }
            if (placed.isEmpty()) {
                item {
                    SunCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Widgets, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(Spacing.md))
                            Text(
                                "No SunCanvas widgets yet. Pick a design below and tap \"Add to home screen\", or long-press your home screen → Widgets → SunCanvas.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            items(placed, key = { "w" + it.appWidgetId }) { inst ->
                val spec = remember(inst.specJson) { WidgetSpec.fromJson(inst.specJson) }
                if (spec != null) DesignRow(spec, data, "Widget #${inst.appWidgetId}", onClick = { onEditWidget(inst.appWidgetId) }) {
                    IconButton(onClick = { onEditWidget(inst.appWidgetId) }) { Icon(Icons.Rounded.Edit, "Edit this widget") }
                }
            }
            if (designs.isNotEmpty()) {
                item { SectionHeader("My designs") }
                items(designs, key = { "d" + it.id }) { d ->
                    val spec = remember(d.specJson) { WidgetSpec.fromJson(d.specJson) }
                    if (spec != null) DesignRow(spec, data, d.name, onClick = { onEditDesign(d.id) }) {
                        IconButton(onClick = { scope.launch { c.widgets.duplicateDesign(d.id) } }) { Icon(Icons.Rounded.ContentCopy, "Duplicate") }
                        IconButton(onClick = { scope.launch { c.widgets.deleteDesign(d.id) } }) { Icon(Icons.Rounded.Delete, "Delete") }
                    }
                }
            }
            item { SectionHeader("Gallery · ${WidgetTemplates.all.size} designs") }
            WidgetTemplates.all.chunked(2).forEach { pair ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(bottom = Spacing.md)) {
                        pair.forEach { t ->
                            SunCard(Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable { onNewFromTemplate(t.id) }, contentPadding = Spacing.sm) {
                                Column {
                                    BoxWithConstraints(Modifier.fillMaxWidth().height(120.dp).clip(MaterialTheme.shapes.small)) {
                                        WallpaperBackground(SampleWallpaper.MOUNTAINS, Modifier.fillMaxSize())
                                        val pv = previewSize(t.sizeClass, maxWidth - 12.dp, 108.dp)
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            WidgetPreview(t.spec, data, pv.width, pv.height, displayScale = pv.scale)
                                        }
                                    }
                                    Text(t.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Spacing.sm))
                                    Text("${t.sizeClass.label} · ${t.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            item {
                Text(
                    "Widgets keep perfect time with Android's native clock views. Sun positions, arcs and countdown values refresh at each sunrise, sunset, golden-hour edge and local midnight, plus every 30 minutes (adjustable in Settings). Exact widget dimensions depend on your launcher.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun DesignRow(spec: WidgetSpec, data: WidgetData?, title: String, onClick: () -> Unit, actions: @Composable () -> Unit) {
    SunCard(Modifier.fillMaxWidth().padding(bottom = Spacing.md).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick), contentPadding = Spacing.sm) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(150.dp).height(84.dp).clip(MaterialTheme.shapes.small)) {
                WallpaperBackground(SampleWallpaper.SUNSET, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val sc = WidgetTemplates.byId(spec.templateId)?.sizeClass ?: WidgetSizeClass.WIDE
                    val pv = previewSize(sc, 140.dp, 76.dp)
                    WidgetPreview(spec, data, pv.width, pv.height, displayScale = pv.scale)
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(spec.layout.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            actions()
        }
    }
}

data class PreviewSize(val width: Dp, val height: Dp, val scale: Float)

/** Typical widget size in dp for a cell span (a launcher cell is roughly 74 × 92 dp). */
fun nominalSize(cellsW: Int, cellsH: Int): Pair<Dp, Dp> =
    (cellsW * 74f + (cellsW - 1) * 8f).dp to (cellsH * 92f + (cellsH - 1) * 8f - 16f).coerceAtLeast(48f).dp

/** Real widget size plus the scale needed to fit it into a box. */
fun previewSize(sc: WidgetSizeClass, maxW: Dp, maxH: Dp): PreviewSize {
    val (w, h) = nominalSize(sc.cellsW, sc.cellsH)
    val scale = minOf(maxW.value / w.value, maxH.value / h.value, 1f)
    return PreviewSize(w, h, scale)
}
