package app.suncanvas.widget.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import app.suncanvas.widget.model.BackgroundSpec
import app.suncanvas.widget.model.BackgroundType
import app.suncanvas.widget.model.FontWeightChoice
import app.suncanvas.widget.model.SceneId
import app.suncanvas.widget.model.WidgetFont
import app.suncanvas.widget.model.WidgetShape
import app.suncanvas.widget.model.WidgetSpec
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Low-level drawing primitives shared by every widget layout and by the in-app preview. */
class WidgetPainter(private val context: Context) {

    private val typefaces = java.util.concurrent.ConcurrentHashMap<Int, Typeface>()
    private val images = LruCache<String, Bitmap>(4)

    fun typeface(font: WidgetFont, weight: FontWeightChoice): Typeface {
        val res = FontResources.fontRes(font, weight)
        return typefaces.getOrPut(res) {
            runCatching { ResourcesCompat.getFont(context, res) }.getOrNull() ?: Typeface.DEFAULT
        }
    }

    // ---------------------------------------------------------------- shapes

    data class ShapeGeometry(val bounds: RectF, val path: Path?, val content: RectF, val radius: Float)

    fun geometry(spec: WidgetSpec, w: Float, h: Float, scale: Float): ShapeGeometry {
        val full = RectF(0f, 0f, w, h)
        val pad = spec.padding * scale
        val minSide = min(w, h)
        return when (spec.shape) {
            WidgetShape.CIRCLE, WidgetShape.RING -> {
                val b = RectF((w - minSide) / 2, (h - minSide) / 2, (w + minSide) / 2, (h + minSide) / 2)
                val p = Path().apply { addOval(b, Path.Direction.CW) }
                val inner = minSide * 0.70f
                val c = RectF(w / 2 - inner / 2, h / 2 - inner / 2, w / 2 + inner / 2, h / 2 + inner / 2)
                ShapeGeometry(b, p, c, minSide / 2)
            }
            WidgetShape.OVAL -> {
                val p = Path().apply { addOval(full, Path.Direction.CW) }
                val c = RectF(w * 0.15f, h * 0.15f, w * 0.85f, h * 0.85f)
                ShapeGeometry(full, p, c, minSide / 2)
            }
            WidgetShape.PILL, WidgetShape.CAPSULE -> {
                val r = minSide / 2
                val p = Path().apply { addRoundRect(full, r, r, Path.Direction.CW) }
                val inset = if (w >= h) r * 0.55f else pad
                val c = RectF(inset + pad * 0.3f, pad, w - inset - pad * 0.3f, h - pad)
                ShapeGeometry(full, p, c, r)
            }
            WidgetShape.HORIZONTAL_BAR -> {
                val bh = min(h, max(minOf(w * 0.30f, 96f * scale), 44f * scale))
                val b = RectF(0f, (h - bh) / 2, w, (h + bh) / 2)
                val r = bh / 2
                val p = Path().apply { addRoundRect(b, r, r, Path.Direction.CW) }
                ShapeGeometry(b, p, RectF(b.left + r * 0.6f, b.top + pad * 0.5f, b.right - r * 0.6f, b.bottom - pad * 0.5f), r)
            }
            WidgetShape.TEXT_ONLY -> ShapeGeometry(full, null, RectF(pad * 0.5f, pad * 0.5f, w - pad * 0.5f, h - pad * 0.5f), 0f)
            WidgetShape.SQUARE -> {
                val p = Path().apply { addRect(full, Path.Direction.CW) }
                ShapeGeometry(full, p, RectF(pad, pad, w - pad, h - pad), 0f)
            }
            else -> {
                val r = (spec.cornerRadius * scale).coerceIn(0f, minSide / 2)
                val p = Path().apply { addRoundRect(full, r, r, Path.Direction.CW) }
                val extra = r * 0.12f
                ShapeGeometry(full, p, RectF(pad + extra, pad, w - pad - extra, h - pad), r)
            }
        }
    }

    // ---------------------------------------------------------------- backgrounds

    /**
     * Fills the shape with its background, applying the background opacity and an anti-aliased mask.
     * [skyElevation] is the Sun's elevation, used by LIVE_SKY and night decorations.
     */
    fun drawBackground(canvas: Canvas, spec: WidgetSpec, geo: ShapeGeometry, scale: Float, skyElevation: Double) {
        val bg = spec.background
        val path = geo.path ?: return
        if (bg.type == BackgroundType.TRANSPARENT || bg.opacity <= 0.001f) return
        val b = geo.bounds
        val layerPaint = Paint().apply { alpha = (bg.opacity.coerceIn(0f, 1f) * 255).toInt() }
        val save = canvas.saveLayer(b, layerPaint)
        paintFill(canvas, bg, b, scale, skyElevation, spec.decorOpacity)
        // Mask to the shape with anti-aliasing: clear everything outside the path. (DST_IN through
        // drawPath would only touch pixels inside the path, leaving the corners filled.)
        val clearOutside = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
        val inverse = Path(path).apply { fillType = Path.FillType.INVERSE_WINDING }
        canvas.drawPath(inverse, clearOutside)
        if (spec.shape == WidgetShape.RING) {
            // Hollow out the centre so the background reads as a ring (wallpaper shows through).
            val inset = min(b.width(), b.height()) * 0.17f
            val inner = RectF(b).apply { inset(inset, inset) }
            canvas.drawOval(inner, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                alpha = 215
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            })
        }
        canvas.restoreToCount(save)
    }

    private fun paintFill(canvas: Canvas, bg: BackgroundSpec, b: RectF, scale: Float, elevation: Double, decor: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (bg.type) {
            BackgroundType.SOLID -> { p.color = bg.color; canvas.drawRect(b, p) }
            BackgroundType.GRADIENT -> { p.shader = linear(b, bg.angle, intArrayOf(bg.color, bg.color2)); canvas.drawRect(b, p) }
            BackgroundType.MULTI_GRADIENT -> {
                val cols = bg.colors.ifEmpty { listOf(bg.color, bg.color2) }
                p.shader = linear(b, bg.angle, (if (cols.size == 1) cols + cols else cols).toIntArray()); canvas.drawRect(b, p)
            }
            BackgroundType.GLASS_DARK -> glass(canvas, b, dark = true, scale = scale)
            BackgroundType.GLASS_LIGHT -> glass(canvas, b, dark = false, scale = scale)
            BackgroundType.SUNRISE -> {
                p.shader = linear(b, 90f, intArrayOf(0xFF1B2A6B.toInt(), 0xFF6B4A9E.toInt(), 0xFFFF8A5C.toInt(), 0xFFFFC56B.toInt()))
                canvas.drawRect(b, p)
                glow(canvas, b.centerX(), b.bottom, b.width() * 0.6f, 0x66FFE0A0, decor)
            }
            BackgroundType.SUNSET -> {
                p.shader = linear(b, 90f, intArrayOf(0xFF2B1B4D.toInt(), 0xFF8E3B8A.toInt(), 0xFFE0566A.toInt(), 0xFFFF9A4D.toInt()))
                canvas.drawRect(b, p)
                glow(canvas, b.centerX(), b.bottom, b.width() * 0.5f, 0x55FFB070, decor)
            }
            BackgroundType.NIGHT_SKY -> {
                p.shader = linear(b, 90f, intArrayOf(0xFF03050F.toInt(), 0xFF0B1026.toInt(), 0xFF1B2A6B.toInt()))
                canvas.drawRect(b, p)
                stars(canvas, b, scale, 0.9f * decor, seed = 7)
            }
            BackgroundType.SCENE -> scene(canvas, bg.scene, b, scale, decor)
            BackgroundType.IMAGE -> image(canvas, bg, b)
            BackgroundType.LIVE_SKY -> liveSky(canvas, b, elevation, scale, decor)
            BackgroundType.TRANSPARENT -> Unit
        }
    }

    fun linear(b: RectF, angleDeg: Float, colors: IntArray): Shader {
        val rad = Math.toRadians(angleDeg.toDouble())
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        val half = (kotlin.math.abs(b.width() * dx) + kotlin.math.abs(b.height() * dy)) / 2
        val cx = b.centerX()
        val cy = b.centerY()
        return LinearGradient(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half, colors, null, Shader.TileMode.CLAMP)
    }

    /**
     * Home-screen widgets cannot blur the wallpaper behind them, so "glass" is approximated with a
     * translucent tint, a soft vertical sheen and a bright top edge.
     */
    private fun glass(canvas: Canvas, b: RectF, dark: Boolean, scale: Float) {
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = linear(b, 90f, if (dark) intArrayOf(0xCC1A2140.toInt(), 0xE00B1026.toInt()) else intArrayOf(0x99FFFFFF.toInt(), 0x55FFFFFF))
        }
        canvas.drawRect(b, base)
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(b.left, b.top, b.left + b.width() * 0.6f, b.bottom,
                intArrayOf(if (dark) 0x26FFFFFF else 0x4DFFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(b, sheen)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) 0x40FFFFFF else 0x99FFFFFF.toInt()
            strokeWidth = 1.2f * scale
        }
        canvas.drawLine(b.left, b.top + scale, b.right, b.top + scale, edge)
    }

    fun glow(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, strength: Float) {
        if (strength <= 0f || radius <= 0f) return
        val a = ((color ushr 24) * strength).toInt().coerceIn(0, 255)
        val c = (a shl 24) or (color and 0xFFFFFF)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, radius, intArrayOf(c, c and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(cx, cy, radius, p)
    }

    fun stars(canvas: Canvas, b: RectF, scale: Float, strength: Float, seed: Int, count: Int = 0) {
        if (strength <= 0f) return
        val rnd = Random(seed)
        val n = if (count > 0) count else ((b.width() * b.height()) / (scale * scale * 260f)).toInt().coerceIn(12, 220)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        repeat(n) {
            val x = b.left + rnd.nextFloat() * b.width()
            val y = b.top + rnd.nextFloat() * b.height() * 0.85f
            p.alpha = ((0.25f + rnd.nextFloat() * 0.75f) * 255 * strength).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, (0.4f + rnd.nextFloat() * 0.9f) * scale, p)
        }
    }

    private fun liveSky(canvas: Canvas, b: RectF, elevation: Double, scale: Float, decor: Float) {
        val colors = when {
            elevation >= 12 -> intArrayOf(0xFF2F6FD6.toInt(), 0xFF5A9BEA.toInt(), 0xFFA9D2F7.toInt())
            elevation >= 4 -> intArrayOf(0xFF3A5DB5.toInt(), 0xFFE8A06B.toInt(), 0xFFFFD08A.toInt())
            elevation >= -1 -> intArrayOf(0xFF33307A.toInt(), 0xFFD9667A.toInt(), 0xFFFFA65C.toInt())
            elevation >= -6 -> intArrayOf(0xFF1A1E52.toInt(), 0xFF5A3F8C.toInt(), 0xFFC4657A.toInt())
            elevation >= -12 -> intArrayOf(0xFF0B1033.toInt(), 0xFF1E2A66.toInt(), 0xFF3B3F86.toInt())
            else -> intArrayOf(0xFF03050F.toInt(), 0xFF0B1026.toInt(), 0xFF16204A.toInt())
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = linear(b, 90f, colors) }
        canvas.drawRect(b, p)
        if (elevation < -4) stars(canvas, b, scale, ((-4 - elevation) / 10.0).toFloat().coerceIn(0f, 1f) * decor, seed = 11)
    }

    private fun scene(canvas: Canvas, scene: SceneId, b: RectF, scale: Float, decor: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun sky(vararg c: Long) { p.shader = linear(b, 90f, c.map { it.toInt() }.toIntArray()); canvas.drawRect(b, p); p.shader = null }
        fun ridge(baseY: Float, amp: Float, freq: Float, phase: Float, color: Long) {
            val path = Path()
            path.moveTo(b.left, b.bottom)
            var x = b.left
            val step = max(2f, b.width() / 80f)
            while (x <= b.right + step) {
                val t = (x - b.left) / b.width()
                val y = baseY - amp * (0.55f * sin(t * freq + phase) + 0.3f * sin(t * freq * 2.3f + phase * 1.7f) + 0.15f * sin(t * freq * 5.1f))
                path.lineTo(x, y)
                x += step
            }
            path.lineTo(b.right, b.bottom); path.close()
            p.color = color.toInt(); canvas.drawPath(path, p)
        }
        when (scene) {
            SceneId.HORIZON -> {
                sky(0xFF0E1A4A, 0xFF5B3F8C, 0xFFFF8A5C, 0xFFFFC56B)
                glow(canvas, b.centerX(), b.top + b.height() * 0.78f, b.width() * 0.45f, 0x99FFD08A.toInt(), decor)
                p.color = 0xFF1A1030.toInt(); canvas.drawRect(b.left, b.top + b.height() * 0.8f, b.right, b.bottom, p)
            }
            SceneId.MOUNTAINS -> {
                sky(0xFF1B2450, 0xFF6A4A8F, 0xFFF08A6A, 0xFFFFC27A)
                glow(canvas, b.left + b.width() * 0.7f, b.top + b.height() * 0.62f, b.width() * 0.35f, 0x88FFD9A0.toInt(), decor)
                ridge(b.top + b.height() * 0.70f, b.height() * 0.16f, 7f, 0.4f, 0xFF4A3466)
                ridge(b.top + b.height() * 0.80f, b.height() * 0.13f, 9f, 2.1f, 0xFF2D2045)
                ridge(b.top + b.height() * 0.90f, b.height() * 0.08f, 12f, 4.0f, 0xFF181127)
            }
            SceneId.DUNES -> {
                sky(0xFFF6D3A0, 0xFFF2B07A, 0xFFE08A5A)
                ridge(b.top + b.height() * 0.72f, b.height() * 0.08f, 4f, 1.0f, 0xFFD9935A)
                ridge(b.top + b.height() * 0.84f, b.height() * 0.07f, 5f, 3.0f, 0xFFB86B3E)
                ridge(b.top + b.height() * 0.94f, b.height() * 0.05f, 6f, 5.0f, 0xFF8C4B2B)
            }
            SceneId.OCEAN -> {
                sky(0xFF0A2A5C, 0xFF1B5F9E, 0xFF7FC3E8)
                p.shader = linear(RectF(b.left, b.top + b.height() * 0.65f, b.right, b.bottom), 90f, intArrayOf(0xFF0E4D7A.toInt(), 0xFF06243F.toInt()))
                canvas.drawRect(b.left, b.top + b.height() * 0.65f, b.right, b.bottom, p); p.shader = null
                p.color = 0x55FFFFFF; p.strokeWidth = scale
                for (i in 0 until 6) {
                    val y = b.top + b.height() * (0.69f + i * 0.05f)
                    canvas.drawLine(b.left + b.width() * (0.1f + i * 0.07f), y, b.right - b.width() * (0.15f + i * 0.04f), y, p)
                }
            }
            SceneId.STARFIELD -> {
                sky(0xFF02030A, 0xFF070B1F, 0xFF101A3D)
                stars(canvas, b, scale, decor, seed = 3, count = 0)
                glow(canvas, b.right * 0.8f, b.top + b.height() * 0.2f, b.width() * 0.25f, 0x337FB2FF, decor)
            }
            SceneId.AURORA -> {
                sky(0xFF020814, 0xFF06202E, 0xFF0B3B3A)
                stars(canvas, b, scale, 0.7f * decor, seed = 5)
                val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    maskFilter = BlurMaskFilter(max(1f, b.height() * 0.08f), BlurMaskFilter.Blur.NORMAL)
                    style = Paint.Style.STROKE
                    strokeWidth = b.height() * 0.10f
                }
                val colors = intArrayOf(0x996EF2C2.toInt(), 0x7766D9FF, 0x66B28CFF)
                colors.forEachIndexed { i, col ->
                    ap.color = col
                    ap.alpha = ((col ushr 24) * decor).toInt().coerceIn(0, 255)
                    val path = Path()
                    path.moveTo(b.left - 10, b.top + b.height() * (0.35f + i * 0.08f))
                    path.cubicTo(b.left + b.width() * 0.3f, b.top + b.height() * (0.10f + i * 0.1f),
                        b.left + b.width() * 0.6f, b.top + b.height() * (0.55f + i * 0.05f), b.right + 10, b.top + b.height() * (0.25f + i * 0.1f))
                    canvas.drawPath(path, ap)
                }
            }
            SceneId.CITY -> {
                sky(0xFF0B1026, 0xFF3B2A6B, 0xFFE0566A)
                val rnd = Random(21)
                var x = b.left
                p.color = 0xFF0A0A14.toInt()
                val win = Paint().apply { color = 0xCCFFD27A.toInt() }
                while (x < b.right) {
                    val bw = b.width() * (0.04f + rnd.nextFloat() * 0.07f)
                    val bh = b.height() * (0.15f + rnd.nextFloat() * 0.35f)
                    canvas.drawRect(x, b.bottom - bh, x + bw, b.bottom, p)
                    if (decor > 0) {
                        var wy = b.bottom - bh + 4 * scale
                        while (wy < b.bottom - 4 * scale) {
                            if (rnd.nextFloat() < 0.35f) canvas.drawRect(x + bw * 0.3f, wy, x + bw * 0.45f, wy + 1.6f * scale, win)
                            wy += 5 * scale
                        }
                    }
                    x += bw + scale
                }
            }
            SceneId.PAPER -> {
                sky(0xFFFBF4EA, 0xFFF3E6D3)
                val rnd = Random(13)
                val fleck = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14000000 }
                repeat(160) { canvas.drawCircle(b.left + rnd.nextFloat() * b.width(), b.top + rnd.nextFloat() * b.height(), 0.5f * scale, fleck) }
            }
        }
    }

    private fun image(canvas: Canvas, bg: BackgroundSpec, b: RectF) {
        val path = bg.imagePath
        val bmp = path?.let { loadImage(it, b.width().toInt(), b.height().toInt()) }
        if (bmp == null) {
            val p = Paint().apply { shader = linear(b, 90f, intArrayOf(bg.color, bg.color2)) }
            canvas.drawRect(b, p)
            return
        }
        // Centre-crop with zoom and pan.
        val zoom = bg.imageZoom.coerceIn(1f, 4f)
        val s = max(b.width() / bmp.width, b.height() / bmp.height) * zoom
        val dw = bmp.width * s
        val dh = bmp.height * s
        val extraX = (dw - b.width()) / 2
        val extraY = (dh - b.height()) / 2
        val left = b.left - extraX - bg.imageOffsetX.coerceIn(-1f, 1f) * extraX
        val top = b.top - extraY - bg.imageOffsetY.coerceIn(-1f, 1f) * extraY
        val m = Matrix().apply { setScale(s, s); postTranslate(left, top) }
        val br = bg.imageBrightness.coerceIn(-1f, 1f) * 90f
        val cm = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, br,
            0f, 1f, 0f, 0f, br,
            0f, 0f, 1f, 0f, br,
            0f, 0f, 0f, 1f, 0f,
        ))
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
            alpha = (bg.imageOpacity.coerceIn(0f, 1f) * 255).toInt()
        }
        canvas.drawBitmap(bmp, m, p)
        if (bg.overlayOpacity > 0f) {
            val o = Paint().apply { color = bg.overlayColor; alpha = (bg.overlayOpacity.coerceIn(0f, 1f) * 255).toInt() }
            canvas.drawRect(b, o)
        }
    }

    private fun loadImage(path: String, targetW: Int, targetH: Int): Bitmap? {
        val key = "$path@$targetW"
        images.get(key)?.let { return it }
        val f = File(path)
        if (!f.exists()) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        val need = max(targetW, targetH) * 2
        while (opts.outWidth / (sample * 2) >= need && opts.outHeight / (sample * 2) >= need) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        images.put(key, bmp)
        return bmp
    }

    // ---------------------------------------------------------------- border & accents

    fun drawBorder(canvas: Canvas, spec: WidgetSpec, geo: ShapeGeometry, scale: Float) {
        val bw = spec.border.width * scale
        val path = geo.path ?: return
        if (bw <= 0f || spec.border.opacity <= 0f || spec.shape == WidgetShape.BORDERLESS) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = bw
            color = spec.border.color
            alpha = (spec.border.opacity.coerceIn(0f, 1f) * 255).toInt()
        }
        val save = canvas.save()
        canvas.clipPath(path)
        canvas.drawPath(path, p.apply { strokeWidth = bw * 2 })
        canvas.restoreToCount(save)
    }

    fun drawCornerAccent(canvas: Canvas, geo: ShapeGeometry, color: Int, strength: Float) {
        val path = geo.path ?: return
        if (strength <= 0f) return
        val b = geo.bounds
        val r = min(b.width(), b.height()) * 0.55f
        val save = canvas.save()
        canvas.clipPath(path)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(b.right, b.top, r, intArrayOf(withAlpha(color, 0.85f * strength), withAlpha(color, 0f)), null, Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(b.right, b.top, r, p)
        canvas.restoreToCount(save)
    }

    // ---------------------------------------------------------------- icons

    enum class Icon { SUNRISE, SUNSET, SUN, MOON, GOLDEN, NOON, HOURGLASS }

    fun drawIcon(canvas: Canvas, icon: Icon, cx: Float, cy: Float, size: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = size * 0.09f
        }
        val r = size / 2
        when (icon) {
            Icon.SUNRISE, Icon.SUNSET -> {
                val horizonY = cy + r * 0.38f
                val sunR = r * 0.42f
                // Half sun above the horizon.
                val oval = RectF(cx - sunR, horizonY - sunR, cx + sunR, horizonY + sunR)
                p.style = Paint.Style.FILL
                canvas.drawArc(oval, 180f, 180f, true, p)
                // Rays.
                p.style = Paint.Style.STROKE
                for (a in listOf(180f, 225f, 270f, 315f, 360f)) {
                    val rad = Math.toRadians(a.toDouble())
                    val x1 = cx + cos(rad).toFloat() * sunR * 1.35f
                    val y1 = horizonY + sin(rad).toFloat() * sunR * 1.35f
                    val x2 = cx + cos(rad).toFloat() * sunR * 1.75f
                    val y2 = horizonY + sin(rad).toFloat() * sunR * 1.75f
                    canvas.drawLine(x1, y1, x2, y2, p)
                }
                canvas.drawLine(cx - r * 0.95f, horizonY, cx + r * 0.95f, horizonY, p)
                // Arrow.
                val ay = horizonY + r * 0.32f
                val dir = if (icon == Icon.SUNRISE) -1 else 1
                val tipY = if (icon == Icon.SUNRISE) ay - r * 0.04f else ay + r * 0.30f
                val baseY = tipY - dir * r * 0.22f
                canvas.drawLine(cx - r * 0.2f, baseY, cx, tipY, p)
                canvas.drawLine(cx + r * 0.2f, baseY, cx, tipY, p)
            }
            Icon.SUN, Icon.NOON, Icon.GOLDEN -> {
                val sunR = r * 0.42f
                p.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, sunR, p)
                p.style = Paint.Style.STROKE
                for (i in 0 until 8) {
                    val rad = Math.toRadians(i * 45.0)
                    canvas.drawLine(
                        cx + cos(rad).toFloat() * sunR * 1.4f, cy + sin(rad).toFloat() * sunR * 1.4f,
                        cx + cos(rad).toFloat() * sunR * 1.95f, cy + sin(rad).toFloat() * sunR * 1.95f, p,
                    )
                }
            }
            Icon.MOON -> {
                val mr = r * 0.62f
                val moon = Path().apply { addCircle(cx, cy, mr, Path.Direction.CW) }
                val cut = Path().apply { addCircle(cx + mr * 0.55f, cy - mr * 0.35f, mr * 0.85f, Path.Direction.CW) }
                moon.op(cut, Path.Op.DIFFERENCE)
                p.style = Paint.Style.FILL
                canvas.drawPath(moon, p)
            }
            Icon.HOURGLASS -> {
                p.style = Paint.Style.STROKE
                val w = r * 0.55f
                val h = r * 0.8f
                val path = Path().apply {
                    moveTo(cx - w, cy - h); lineTo(cx + w, cy - h); lineTo(cx - w, cy + h); lineTo(cx + w, cy + h); close()
                }
                canvas.drawPath(path, p)
            }
        }
    }

    companion object {
        fun withAlpha(color: Int, alpha: Float): Int {
            val a = ((color ushr 24) * alpha.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
            return (a shl 24) or (color and 0x00FFFFFF)
        }

        /** WCAG relative luminance. */
        fun luminance(color: Int): Double {
            fun ch(c: Int): Double {
                val s = c / 255.0
                return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * ch(Color.red(color)) + 0.7152 * ch(Color.green(color)) + 0.0722 * ch(Color.blue(color))
        }

        fun contrast(a: Int, b: Int): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
        }

        /** Composite [fg] (with alpha) over opaque [bg]. */
        fun over(fg: Int, bg: Int): Int {
            val a = Color.alpha(fg) / 255f
            return Color.rgb(
                (Color.red(fg) * a + Color.red(bg) * (1 - a)).toInt(),
                (Color.green(fg) * a + Color.green(bg) * (1 - a)).toInt(),
                (Color.blue(fg) * a + Color.blue(bg) * (1 - a)).toInt(),
            )
        }
    }
}
