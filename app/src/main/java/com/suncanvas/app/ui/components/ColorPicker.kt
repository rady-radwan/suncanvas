package com.suncanvas.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

val SwatchColors = listOf(
    0xFFFFFFFF, 0xFFEEF1FA, 0xFFBDBDBD, 0xFF757575, 0xFF212121, 0xFF000000,
    0xFFFFE0B2, 0xFFFFC870, 0xFFFFB547, 0xFFFF9A4D, 0xFFFF7A3D, 0xFFE0566A,
    0xFFB0457B, 0xFF8E3B8A, 0xFF6B4A9E, 0xFF3B1C5A, 0xFF1B2A6B, 0xFF0B1026,
    0xFF7FB2FF, 0xFF3D6FD1, 0xFF0E7490, 0xFF6EF2C2, 0xFF2E7D32, 0xFFF2D9B3,
).map { it.toInt() }

/** A small swatch button showing a colour; opens the picker on click. */
@Composable
fun ColorSwatchButton(label: String, color: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier.clickable(onClickLabel = "Change $label") { open = true }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(checker()).background(Color(color))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(toHex(color), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open) ColorPickerDialog(label, color, onDismiss = { open = false }, onPick = { onPick(it); open = false })
}

private fun checker() = Brush.linearGradient(listOf(Color(0xFFCCCCCC), Color(0xFFFFFFFF), Color(0xFFCCCCCC)))

fun toHex(argb: Int): String {
    val a = (argb ushr 24) and 0xFF
    return if (a == 0xFF) "#%06X".format(argb and 0xFFFFFF) else "#%08X".format(argb)
}

fun parseHex(text: String): Int? {
    val t = text.trim().removePrefix("#")
    return when (t.length) {
        6 -> t.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
        8 -> t.toLongOrNull(16)?.toInt()
        3 -> t.map { "$it$it" }.joinToString("").toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
        else -> null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(title: String, initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var alpha by remember { mutableFloatStateOf(((initial ushr 24) and 0xFF) / 255f) }
    val current = android.graphics.Color.HSVToColor((alpha * 255).toInt(), floatArrayOf(hue, sat, value))
    var hex by remember { mutableStateOf(toHex(initial)) }

    fun setFrom(argb: Int) {
        val f = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, f)
        hue = f[0]; sat = f[1]; value = f[2]; alpha = ((argb ushr 24) and 0xFF) / 255f
        hex = toHex(argb)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Saturation / value square.
                Canvas(
                    Modifier.fillMaxWidth().height(150.dp).clip(MaterialTheme.shapes.small)
                        .semantics { contentDescription = "Saturation and brightness" }
                        .pointerInput(Unit) {
                            fun upd(o: Offset) {
                                sat = (o.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (o.y / size.height).coerceIn(0f, 1f)
                                hex = toHex(android.graphics.Color.HSVToColor((alpha * 255).toInt(), floatArrayOf(hue, sat, value)))
                            }
                            detectTapGestures { upd(it) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                sat = (change.position.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                                hex = toHex(android.graphics.Color.HSVToColor((alpha * 255).toInt(), floatArrayOf(hue, sat, value)))
                            }
                        },
                ) {
                    val pure = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                    drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val p = Offset(sat * size.width, (1 - value) * size.height)
                    drawCircle(Color.White, 9.dp.toPx(), p, style = Stroke(2.5.dp.toPx()))
                    drawCircle(Color.Black.copy(alpha = 0.4f), 10.5.dp.toPx(), p, style = Stroke(1.dp.toPx()))
                }
                Text("Hue", style = MaterialTheme.typography.labelMedium)
                Slider(value = hue, onValueChange = { hue = it; hex = toHex(android.graphics.Color.HSVToColor((alpha * 255).toInt(), floatArrayOf(hue, sat, value))) }, valueRange = 0f..360f)
                Text("Opacity ${(alpha * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(value = alpha, onValueChange = { alpha = it; hex = toHex(android.graphics.Color.HSVToColor((alpha * 255).toInt(), floatArrayOf(hue, sat, value))) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(current)).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { t -> hex = t; parseHex(t)?.let { setFrom(it); hex = t } },
                        label = { Text("HEX") }, singleLine = true, isError = parseHex(hex) == null,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SwatchColors.forEach { sw ->
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(Color(sw))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable(onClickLabel = toHex(sw)) { setFrom(sw) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Suppress("unused")
private fun Color.argb() = toArgb()
