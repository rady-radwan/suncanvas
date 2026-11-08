package com.suncanvas.app.ui.screens.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.suncanvas.app.container
import com.suncanvas.app.widget.model.WidgetSpec
import com.suncanvas.app.widget.render.WidgetData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

/** Sample wallpapers used to judge contrast in the editor. */
enum class SampleWallpaper(val label: String) { DARK("Dark"), LIGHT("Light"), SUNSET("Colorful"), MOUNTAINS("Photo"), GRAY("Mid-gray") }

@Composable
fun WallpaperBackground(w: SampleWallpaper, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        when (w) {
            SampleWallpaper.DARK -> drawRect(Brush.verticalGradient(listOf(Color(0xFF14161F), Color(0xFF070809))))
            SampleWallpaper.LIGHT -> drawRect(Brush.verticalGradient(listOf(Color(0xFFF7F4EF), Color(0xFFE6E1D8))))
            SampleWallpaper.GRAY -> drawRect(Color(0xFF8A8A8A))
            SampleWallpaper.SUNSET -> drawRect(Brush.linearGradient(listOf(Color(0xFF3B1C5A), Color(0xFFE0566A), Color(0xFFFFB547)), Offset.Zero, Offset(size.width, size.height)))
            SampleWallpaper.MOUNTAINS -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF6FA8DC), Color(0xFFCFE6F5))))
                fun ridge(base: Float, amp: Float, f: Float, c: Color) {
                    val p = Path()
                    p.moveTo(0f, size.height)
                    var x = 0f
                    while (x <= size.width) {
                        p.lineTo(x, base - amp * sin(x / size.width * f).toFloat() - amp * 0.4f * sin(x / size.width * f * 2.7f).toFloat())
                        x += 6f
                    }
                    p.lineTo(size.width, size.height); p.close()
                    drawPath(p, c)
                }
                ridge(size.height * 0.62f, size.height * 0.10f, 9f, Color(0xFF5D7A8C))
                ridge(size.height * 0.75f, size.height * 0.08f, 13f, Color(0xFF3E5A3C))
                ridge(size.height * 0.88f, size.height * 0.05f, 17f, Color(0xFF2C3E2A))
            }
        }
    }
}

/**
 * Renders [spec] with the exact same engine as the home-screen widget (painted clock instead of a
 * native TextClock) at its real size in dp, optionally displayed scaled down.
 */
@Composable
fun WidgetPreview(spec: WidgetSpec, data: WidgetData?, width: Dp, height: Dp, modifier: Modifier = Modifier, displayScale: Float = 1f) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(spec, data, width, height) {
        if (data == null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.Default) {
            context.container.renderer.render(spec, data, width.value, height.value, density, live = false).bitmap.asImageBitmap()
        }
    }
    Box(modifier.size(width * displayScale, height * displayScale).semantics { contentDescription = "Preview of ${spec.name} widget" }, contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
            ?: Box(Modifier.fillMaxSize().background(Color(0x22FFFFFF)))
    }
}
