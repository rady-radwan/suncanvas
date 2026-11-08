package com.suncanvas.app.widget.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Arrangement engines. Each template picks one and the renderer adapts it to the widget's real size. */
@Serializable
enum class WidgetLayout(val label: String) {
    STACK("Text stack"),
    CLOCK("Clock hero"),
    SOLAR_DIAL("24-hour solar dial"),
    PROGRESS_RING("Progress ring"),
    ARC("Sun arc"),
    BAR("Horizontal bar"),
    DASHBOARD("Dashboard"),
    MULTI_CITY("Multiple cities"),
    GOLDEN("Golden hour"),
    ICON("Compact icon"),
    SPLIT("Day / night split"),
    NEXT_EVENT("Next event"),
}

@Serializable
enum class WidgetShape(val label: String) {
    SQUARE("Square"),
    ROUNDED("Rounded rectangle"),
    PILL("Pill"),
    CIRCLE("Circle"),
    OVAL("Oval"),
    CAPSULE("Capsule"),
    CORNER_ACCENT("Corner-accent card"),
    BORDERLESS("Borderless"),
    TEXT_ONLY("Minimal text-only"),
    RING("Circular ring"),
    HORIZONTAL_BAR("Horizontal bar"),
    CUSTOM("Custom corner radius"),
}

@Serializable
enum class BackgroundType(val label: String) {
    SOLID("Solid color"),
    GRADIENT("Two-color gradient"),
    MULTI_GRADIENT("Multicolor gradient"),
    TRANSPARENT("Transparent"),
    GLASS_DARK("Dark glass"),
    GLASS_LIGHT("Light glass"),
    SUNRISE("Sunrise gradient"),
    SUNSET("Sunset gradient"),
    NIGHT_SKY("Night sky"),
    SCENE("Built-in scene"),
    IMAGE("Photo from gallery"),
    LIVE_SKY("Live sky (follows the Sun)"),
}

@Serializable
enum class SceneId(val label: String) {
    HORIZON("Horizon"),
    MOUNTAINS("Mountains"),
    DUNES("Desert dunes"),
    OCEAN("Ocean"),
    STARFIELD("Starfield"),
    AURORA("Aurora"),
    CITY("City skyline"),
    PAPER("Warm paper"),
}

@Serializable
enum class WidgetFont(val label: String) {
    INTER("Modern Sans"),
    NUNITO("Rounded"),
    POPPINS("Geometric"),
    SPACE_GROTESK("Grotesk"),
    PLAYFAIR("Elegant Serif"),
    CORMORANT("Classic Serif"),
    JETBRAINS_MONO("Monospace"),
    ORBITRON("Digital"),
    OSWALD("Condensed"),
    BEBAS("Bold Display"),
    QUICKSAND("Soft Rounded"),
}

@Serializable
enum class FontWeightChoice(val label: String) { LIGHT("Light"), REGULAR("Regular"), BOLD("Bold") }

@Serializable
enum class TextCase(val label: String) { AS_IS("As written"), UPPER("UPPERCASE"), LOWER("lowercase"), TITLE("Title Case") }

@Serializable
enum class Align(val label: String) { START("Left"), CENTER("Center"), END("Right") }

@Serializable
enum class WidgetElement(val label: String) {
    CITY("City name"),
    COUNTRY("Country"),
    TIME("Current time"),
    DATE("Current date"),
    SUNRISE("Sunrise time"),
    SUNSET("Sunset time"),
    DAYLIGHT("Daylight duration"),
    COUNTDOWN_SUNRISE("Countdown to sunrise"),
    COUNTDOWN_SUNSET("Countdown to sunset"),
    NEXT_EVENT("Next solar event"),
    GOLDEN_HOUR("Golden hour"),
    SOLAR_NOON("Solar noon"),
    PROGRESS("Daylight progress"),
    SUN_ARC("Sun arc"),
    DAY_NIGHT("Day/night indicator"),
    TZ_ABBR("Time-zone abbreviation"),
    ICONS("Sunrise/sunset icons"),
}

@Serializable
data class BackgroundSpec(
    val type: BackgroundType = BackgroundType.SOLID,
    val color: Int = 0xFF101733.toInt(),
    val color2: Int = 0xFF2A1B4D.toInt(),
    val colors: List<Int> = listOf(0xFF1B2A6B.toInt(), 0xFF8E3B8A.toInt(), 0xFFFF8A3D.toInt()),
    /** Gradient angle in degrees (0 = left→right, 90 = top→bottom). */
    val angle: Float = 90f,
    /** 0 = fully transparent background, 1 = opaque. */
    val opacity: Float = 1f,
    val scene: SceneId = SceneId.HORIZON,
    val imagePath: String? = null,
    val imageZoom: Float = 1f,
    /** −1..1 pan of the crop window. */
    val imageOffsetX: Float = 0f,
    val imageOffsetY: Float = 0f,
    /** −1 (dark) .. 1 (bright). */
    val imageBrightness: Float = 0f,
    val imageOpacity: Float = 1f,
    /** Tint laid over photos to keep text legible. */
    val overlayColor: Int = 0xFF000000.toInt(),
    val overlayOpacity: Float = 0.25f,
)

@Serializable
data class BorderSpec(
    val width: Float = 0f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val opacity: Float = 0.25f,
)

@Serializable
data class ColorSpec(
    val text: Int = 0xFFFFFFFF.toInt(),
    val secondaryText: Int = 0xCCFFFFFF.toInt(),
    val sunrise: Int = 0xFFFFB547.toInt(),
    val sunset: Int = 0xFFFF7A3D.toInt(),
    val arc: Int = 0xFFFFC870.toInt(),
    val icon: Int = 0xFFFFC14D.toInt(),
    val accent: Int = 0xFF7FB2FF.toInt(),
)

@Serializable
data class TypographySpec(
    val font: WidgetFont = WidgetFont.INTER,
    val weight: FontWeightChoice = FontWeightChoice.REGULAR,
    val clockFont: WidgetFont? = null,
    val clockWeight: FontWeightChoice = FontWeightChoice.LIGHT,
    /** Global text scale. */
    val scale: Float = 1f,
    val clockScale: Float = 1f,
    val labelScale: Float = 1f,
    /** Extra letter spacing in em (−0.05..0.4). */
    val letterSpacing: Float = 0f,
    val case: TextCase = TextCase.AS_IS,
    val labelCase: TextCase = TextCase.UPPER,
    val shadow: Boolean = false,
)

@Serializable
data class WidgetSpec(
    val version: Int = 1,
    val templateId: String,
    val name: String,
    val layout: WidgetLayout,
    val shape: WidgetShape = WidgetShape.ROUNDED,
    /** Corner radius in dp (ROUNDED / CUSTOM / CORNER_ACCENT). */
    val cornerRadius: Float = 26f,
    val background: BackgroundSpec = BackgroundSpec(),
    val border: BorderSpec = BorderSpec(),
    val colors: ColorSpec = ColorSpec(),
    val typography: TypographySpec = TypographySpec(),
    val elements: Set<WidgetElement> = setOf(WidgetElement.SUNRISE, WidgetElement.SUNSET),
    val align: Align = Align.CENTER,
    /** Inner padding in dp. */
    val padding: Float = 14f,
    /** Vertical spacing multiplier between rows. */
    val spacing: Float = 1f,
    /** Opacity of decorative overlays: glows, stars, accent shapes, tracks. */
    val decorOpacity: Float = 1f,
    /** Opacity of all text. */
    val textOpacity: Float = 1f,
    /** Location to show; null = follow the app's default location. */
    val locationId: Long? = null,
    /** Additional cities for multi-city layouts. */
    val extraLocationIds: List<Long> = emptyList(),
    /** Use a native, always-accurate TextClock for the time (recommended). */
    val liveClock: Boolean = true,
    /** Use a native ticking Chronometer for countdowns instead of a value refreshed periodically. */
    val liveCountdown: Boolean = false,
) {
    fun has(e: WidgetElement) = e in elements

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
        fun fromJson(s: String): WidgetSpec? = runCatching { json.decodeFromString(serializer(), s) }.getOrNull()
    }
}
