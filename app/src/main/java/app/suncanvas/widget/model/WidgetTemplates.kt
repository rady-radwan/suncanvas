package app.suncanvas.widget.model

import app.suncanvas.widget.model.WidgetElement.CITY
import app.suncanvas.widget.model.WidgetElement.COUNTDOWN_SUNSET
import app.suncanvas.widget.model.WidgetElement.DATE
import app.suncanvas.widget.model.WidgetElement.DAYLIGHT
import app.suncanvas.widget.model.WidgetElement.DAY_NIGHT
import app.suncanvas.widget.model.WidgetElement.GOLDEN_HOUR
import app.suncanvas.widget.model.WidgetElement.ICONS
import app.suncanvas.widget.model.WidgetElement.NEXT_EVENT
import app.suncanvas.widget.model.WidgetElement.PROGRESS
import app.suncanvas.widget.model.WidgetElement.SOLAR_NOON
import app.suncanvas.widget.model.WidgetElement.SUNRISE
import app.suncanvas.widget.model.WidgetElement.SUNSET
import app.suncanvas.widget.model.WidgetElement.SUN_ARC
import app.suncanvas.widget.model.WidgetElement.TIME
import app.suncanvas.widget.model.WidgetElement.TZ_ABBR

/** Which home-screen receiver (and default cell size) a template is pinned with. */
enum class WidgetSizeClass(val label: String, val cellsW: Int, val cellsH: Int) {
    SMALL("2×1", 2, 1),
    SQUARE("2×2", 2, 2),
    WIDE("4×2", 4, 2),
    LARGE("4×4", 4, 4),
}

data class WidgetTemplate(
    val id: String,
    val name: String,
    val description: String,
    val sizeClass: WidgetSizeClass,
    val spec: WidgetSpec,
)

data class WidgetPalette(
    val name: String,
    val colors: ColorSpec,
    val background: Int,
    val background2: Int,
    val border: Int = 0xFFFFFFFF.toInt(),
)

object WidgetPalettes {
    private fun c(v: Long) = v.toInt()

    val all = listOf(
        WidgetPalette("Sunrise Orange", ColorSpec(c(0xFFFFFFFF), c(0xE6FFF1E0), c(0xFFFFD27A), c(0xFFFF8A4C), c(0xFFFFC46B), c(0xFFFFD27A), c(0xFFFFE0B2)), c(0xFFFF7A3D), c(0xFFFFB547)),
        WidgetPalette("Golden Hour", ColorSpec(c(0xFF2B1600), c(0xCC4A2A00), c(0xFF8A4B00), c(0xFFB3401A), c(0xFF8A4B00), c(0xFF9E5800), c(0xFFB3401A)), c(0xFFFFD27A), c(0xFFFFA94D), c(0xFF5A3300)),
        WidgetPalette("Sunset Purple", ColorSpec(c(0xFFFFFFFF), c(0xCCF3E5FF), c(0xFFFFC07A), c(0xFFFF7FA8), c(0xFFFFB0C8), c(0xFFFFC07A), c(0xFFC9A6FF)), c(0xFF3B1C5A), c(0xFFB0457B)),
        WidgetPalette("Midnight Blue", ColorSpec(c(0xFFEEF2FF), c(0xB3C7D2F5), c(0xFFFFB547), c(0xFFFF7A3D), c(0xFF7FB2FF), c(0xFFFFC14D), c(0xFF7FB2FF)), c(0xFF0B1026), c(0xFF1B2A6B)),
        WidgetPalette("Pure Black", ColorSpec(c(0xFFFFFFFF), c(0x99FFFFFF), c(0xFFFFB547), c(0xFFFF7A3D), c(0xFFFFFFFF), c(0xFFFFB547), c(0xFFFFB547)), c(0xFF000000), c(0xFF000000)),
        WidgetPalette("Pure White", ColorSpec(c(0xFF111111), c(0x99111111), c(0xFFD9822B), c(0xFFD9542B), c(0xFF111111), c(0xFFD9822B), c(0xFFD9822B)), c(0xFFFFFFFF), c(0xFFFFFFFF), c(0xFF000000)),
        WidgetPalette("Warm Minimal", ColorSpec(c(0xFF3A2E25), c(0x993A2E25), c(0xFFC7782B), c(0xFFB5523A), c(0xFFC7782B), c(0xFFC7782B), c(0xFF8C6E58)), c(0xFFF7EFE5), c(0xFFEADBC8), c(0xFF3A2E25)),
        WidgetPalette("Ocean Blue", ColorSpec(c(0xFFF0FBFF), c(0xB3D3F1FF), c(0xFFFFD58A), c(0xFFFF9E7A), c(0xFF8BE0FF), c(0xFFFFD58A), c(0xFF8BE0FF)), c(0xFF0A3D62), c(0xFF0E7490)),
        WidgetPalette("Desert Sand", ColorSpec(c(0xFF3D2A14), c(0x993D2A14), c(0xFFB86B1F), c(0xFF9C3D1C), c(0xFFB86B1F), c(0xFFB86B1F), c(0xFF6B4E2E)), c(0xFFF2D9B3), c(0xFFE0B884), c(0xFF3D2A14)),
        WidgetPalette("Monochrome", ColorSpec(c(0xFFFFFFFF), c(0x99FFFFFF), c(0xFFFFFFFF), c(0xFFBDBDBD), c(0xFFFFFFFF), c(0xFFFFFFFF), c(0xFFBDBDBD)), c(0xFF1C1C1E), c(0xFF2C2C2E)),
        WidgetPalette("Aurora", ColorSpec(c(0xFFE9FFF7), c(0xB3BDF5E3), c(0xFF9CFFD9), c(0xFFB28CFF), c(0xFF6EF2C2), c(0xFF9CFFD9), c(0xFFB28CFF)), c(0xFF051C2C), c(0xFF0B3B3A)),
        WidgetPalette("Rose Dawn", ColorSpec(c(0xFF3A1A24), c(0x993A1A24), c(0xFFE07A5F), c(0xFFB5446E), c(0xFFE07A5F), c(0xFFE07A5F), c(0xFFB5446E)), c(0xFFFFE4E1), c(0xFFFFC9C2), c(0xFF3A1A24)),
    )

    fun apply(spec: WidgetSpec, p: WidgetPalette): WidgetSpec = spec.copy(
        colors = p.colors,
        background = spec.background.copy(color = p.background, color2 = p.background2),
        border = spec.border.copy(color = p.border),
    )
}

object WidgetTemplates {
    private fun c(v: Long) = v.toInt()

    private val nightBg = BackgroundSpec(type = BackgroundType.SOLID, color = c(0xFF0B1026), color2 = c(0xFF1B2A6B))

    val all: List<WidgetTemplate> = listOf(
        WidgetTemplate(
            "minimal", "Minimal Sunrise & Sunset", "Two clean times with tiny icons.", WidgetSizeClass.SMALL,
            WidgetSpec(
                templateId = "minimal", name = "Minimal", layout = WidgetLayout.STACK,
                shape = WidgetShape.ROUNDED, cornerRadius = 22f,
                background = BackgroundSpec(type = BackgroundType.GLASS_DARK, opacity = 0.55f),
                elements = setOf(SUNRISE, SUNSET, ICONS),
                typography = TypographySpec(font = WidgetFont.INTER, weight = FontWeightChoice.REGULAR),
            ),
        ),
        WidgetTemplate(
            "digital", "Digital Clock + Sun", "Large live clock with today's sunrise and sunset.", WidgetSizeClass.WIDE,
            WidgetSpec(
                templateId = "digital", name = "Digital Clock", layout = WidgetLayout.CLOCK,
                background = nightBg.copy(type = BackgroundType.GRADIENT, angle = 120f),
                elements = setOf(TIME, DATE, CITY, SUNRISE, SUNSET, ICONS),
                typography = TypographySpec(font = WidgetFont.INTER, clockFont = WidgetFont.SPACE_GROTESK, clockWeight = FontWeightChoice.LIGHT),
            ),
        ),
        WidgetTemplate(
            "solar_dial", "Circular Solar Clock", "A 24-hour dial with day, twilight and night, and the Sun's position.", WidgetSizeClass.SQUARE,
            WidgetSpec(
                templateId = "solar_dial", name = "Solar Dial", layout = WidgetLayout.SOLAR_DIAL,
                shape = WidgetShape.CIRCLE, background = nightBg,
                elements = setOf(TIME, SUNRISE, SUNSET, DAY_NIGHT),
                typography = TypographySpec(font = WidgetFont.SPACE_GROTESK, clockWeight = FontWeightChoice.REGULAR),
            ),
        ),
        WidgetTemplate(
            "solar_arc", "Solar Arc", "The Sun travelling across a curved sky path.", WidgetSizeClass.WIDE,
            WidgetSpec(
                templateId = "solar_arc", name = "Solar Arc", layout = WidgetLayout.ARC,
                background = BackgroundSpec(type = BackgroundType.LIVE_SKY),
                elements = setOf(SUN_ARC, SUNRISE, SUNSET, CITY, PROGRESS, ICONS),
                typography = TypographySpec(font = WidgetFont.POPPINS),
            ),
        ),
        WidgetTemplate(
            "horizontal_bar", "Elegant Horizontal Bar", "Sunrise ─ progress ─ sunset in one line.", WidgetSizeClass.SMALL,
            WidgetSpec(
                templateId = "horizontal_bar", name = "Horizontal Bar", layout = WidgetLayout.BAR,
                shape = WidgetShape.PILL, background = BackgroundSpec(type = BackgroundType.GLASS_DARK, opacity = 0.7f),
                elements = setOf(SUNRISE, SUNSET, PROGRESS, ICONS),
                typography = TypographySpec(font = WidgetFont.QUICKSAND, weight = FontWeightChoice.BOLD),
            ),
        ),
        WidgetTemplate(
            "vertical_card", "Vertical Information Card", "City, date and the day's key times stacked.", WidgetSizeClass.SQUARE,
            WidgetSpec(
                templateId = "vertical_card", name = "Vertical Card", layout = WidgetLayout.STACK,
                shape = WidgetShape.ROUNDED, cornerRadius = 28f,
                background = BackgroundSpec(type = BackgroundType.SUNSET, angle = 90f),
                elements = setOf(CITY, DATE, SUNRISE, SUNSET, DAYLIGHT, ICONS),
                align = Align.START,
                typography = TypographySpec(font = WidgetFont.POPPINS),
            ),
        ),
        WidgetTemplate(
            "transparent_text", "Transparent Text", "Just text over your wallpaper, with a soft shadow.", WidgetSizeClass.WIDE,
            WidgetSpec(
                templateId = "transparent_text", name = "Transparent", layout = WidgetLayout.CLOCK,
                shape = WidgetShape.TEXT_ONLY, background = BackgroundSpec(type = BackgroundType.TRANSPARENT, opacity = 0f),
                elements = setOf(TIME, DATE, SUNRISE, SUNSET),
                typography = TypographySpec(font = WidgetFont.INTER, clockFont = WidgetFont.INTER, clockWeight = FontWeightChoice.LIGHT, shadow = true),
            ),
        ),
        WidgetTemplate(
            "glass", "Glass", "Frosted glass panel with a live clock.", WidgetSizeClass.WIDE,
            WidgetSpec(
                templateId = "glass", name = "Glass", layout = WidgetLayout.CLOCK,
                background = BackgroundSpec(type = BackgroundType.GLASS_LIGHT, opacity = 0.35f),
                border = BorderSpec(width = 1f, opacity = 0.45f),
                elements = setOf(TIME, CITY, SUNRISE, SUNSET, ICONS),
                typography = TypographySpec(font = WidgetFont.NUNITO, clockFont = WidgetFont.NUNITO, clockWeight = FontWeightChoice.LIGHT, shadow = true),
            ),
        ),
        WidgetTemplate(
            "dashboard", "Modern Dashboard", "Tiles for sunrise, sunset, daylight and what's next.", WidgetSizeClass.WIDE,
            WidgetSpec(
                templateId = "dashboard", name = "Dashboard", layout = WidgetLayout.DASHBOARD,
                background = nightBg,
                elements = setOf(CITY, SUNRISE, SUNSET, DAYLIGHT, NEXT_EVENT, ICONS),
                typography = TypographySpec(font = WidgetFont.SPACE_GROTESK),
            ),
        ),
        WidgetTemplate(
            "progress_circle", "Daylight Progress Circle", "How much of today's daylight has passed.", WidgetSizeClass.SQUARE,
            WidgetSpec(
                templateId = "progress_circle", name = "Progress Circle", layout = WidgetLayout.PROGRESS_RING,
                shape = WidgetShape.CIRCLE, background = nightBg,
                elements = setOf(PROGRESS, SUNSET, DAY_NIGHT),
                typography = TypographySpec(font = WidgetFont.NUNITO, weight = FontWeightChoice.BOLD),
            ),
        ),
        WidgetTemplate(
            "world_clock", "World Clock", "Live time in several cities with day/night dots.", WidgetSizeClass.WIDE,
            WidgetSpec(
                templateId = "world_clock", name = "World Clock", layout = WidgetLayout.MULTI_CITY,
                background = nightBg.copy(type = BackgroundType.GRADIENT),
                elements = setOf(CITY, TIME, DAY_NIGHT, TZ_ABBR),
                typography = TypographySpec(font = WidgetFont.INTER),
            ),
        ),
        WidgetTemplate(
            "multi_city", "Multi-City Sunrise & Sunset", "Sunrise and sunset for each of your cities.", WidgetSizeClass.LARGE,
            WidgetSpec(
                templateId = "multi_city", name = "Multi-City", layout = WidgetLayout.MULTI_CITY,
                background = nightBg,
                elements = setOf(CITY, SUNRISE, SUNSET, DAY_NIGHT, ICONS),
                typography = TypographySpec(font = WidgetFont.INTER),
            ),
        ),
        WidgetTemplate(
            "golden_hour", "Golden Hour", "When the warm light starts and ends today.", WidgetSizeClass.WIDE,
            WidgetSpec(
                templateId = "golden_hour", name = "Golden Hour", layout = WidgetLayout.GOLDEN,
                background = BackgroundSpec(type = BackgroundType.GRADIENT, color = c(0xFFFFE2A6), color2 = c(0xFFFFA65C), angle = 115f),
                colors = WidgetPalettes.all[1].colors,
                elements = setOf(GOLDEN_HOUR, CITY, SUNSET),
                typography = TypographySpec(font = WidgetFont.PLAYFAIR),
            ),
        ),
        WidgetTemplate(
            "compact_icon", "Compact Icon", "1×1 — icon and the next sunrise or sunset.", WidgetSizeClass.SQUARE,
            WidgetSpec(
                templateId = "compact_icon", name = "Compact", layout = WidgetLayout.ICON,
                shape = WidgetShape.CIRCLE, background = nightBg,
                elements = setOf(NEXT_EVENT, ICONS),
                padding = 8f,
                typography = TypographySpec(font = WidgetFont.SPACE_GROTESK, weight = FontWeightChoice.BOLD),
            ),
        ),
        WidgetTemplate(
            "large_solar", "Large Solar Information", "Everything: arc, times, twilight, golden hour.", WidgetSizeClass.LARGE,
            WidgetSpec(
                templateId = "large_solar", name = "Large Solar", layout = WidgetLayout.DASHBOARD,
                background = BackgroundSpec(type = BackgroundType.LIVE_SKY),
                elements = setOf(CITY, DATE, TIME, SUN_ARC, SUNRISE, SUNSET, DAYLIGHT, SOLAR_NOON, GOLDEN_HOUR, NEXT_EVENT, ICONS, PROGRESS),
                typography = TypographySpec(font = WidgetFont.INTER, clockFont = WidgetFont.SPACE_GROTESK),
            ),
        ),
        WidgetTemplate(
            "split", "Day & Night Split", "Warm sunrise half, cool sunset half.", WidgetSizeClass.WIDE,
            WidgetSpec(
                templateId = "split", name = "Split", layout = WidgetLayout.SPLIT,
                background = BackgroundSpec(type = BackgroundType.MULTI_GRADIENT, angle = 0f,
                    colors = listOf(c(0xFFFF9A4D), c(0xFFE3567A), c(0xFF2B2A6B), c(0xFF0B1026))),
                elements = setOf(SUNRISE, SUNSET, DAYLIGHT, ICONS),
                typography = TypographySpec(font = WidgetFont.OSWALD, weight = FontWeightChoice.REGULAR),
            ),
        ),
        WidgetTemplate(
            "horizon_scene", "Horizon Scene", "Mountain silhouette with the sun path above it.", WidgetSizeClass.WIDE,
            WidgetSpec(
                templateId = "horizon_scene", name = "Horizon", layout = WidgetLayout.ARC,
                background = BackgroundSpec(type = BackgroundType.SCENE, scene = SceneId.MOUNTAINS),
                elements = setOf(SUN_ARC, SUNRISE, SUNSET, ICONS),
                typography = TypographySpec(font = WidgetFont.QUICKSAND, weight = FontWeightChoice.BOLD, shadow = true),
            ),
        ),
        WidgetTemplate(
            "next_event", "Next Event Countdown", "Big countdown to the next sunrise or sunset.", WidgetSizeClass.SQUARE,
            WidgetSpec(
                templateId = "next_event", name = "Countdown", layout = WidgetLayout.NEXT_EVENT,
                shape = WidgetShape.CORNER_ACCENT, cornerRadius = 28f,
                background = nightBg,
                elements = setOf(NEXT_EVENT, CITY, ICONS),
                typography = TypographySpec(font = WidgetFont.BEBAS, clockFont = WidgetFont.BEBAS, letterSpacing = 0.04f),
            ),
        ),
        WidgetTemplate(
            "digital_mono", "Retro Digital", "Monospace LCD-style clock with sun times.", WidgetSizeClass.WIDE,
            WidgetSpec(
                templateId = "digital_mono", name = "Retro Digital", layout = WidgetLayout.CLOCK,
                shape = WidgetShape.SQUARE, cornerRadius = 6f,
                background = BackgroundSpec(type = BackgroundType.SOLID, color = c(0xFF050505)),
                colors = ColorSpec(text = c(0xFFFFB547), secondaryText = c(0xB3FFB547), sunrise = c(0xFFFFB547), sunset = c(0xFFFF7A3D), arc = c(0xFFFFB547), icon = c(0xFFFFB547), accent = c(0xFFFF7A3D)),
                elements = setOf(TIME, SUNRISE, SUNSET, TZ_ABBR),
                typography = TypographySpec(font = WidgetFont.JETBRAINS_MONO, clockFont = WidgetFont.ORBITRON, clockWeight = FontWeightChoice.REGULAR, labelCase = TextCase.UPPER),
            ),
        ),
        WidgetTemplate(
            "paper", "Warm Paper", "Serif typography on warm paper — calm and classic.", WidgetSizeClass.SQUARE,
            WidgetSpec(
                templateId = "paper", name = "Warm Paper", layout = WidgetLayout.STACK,
                background = BackgroundSpec(type = BackgroundType.SCENE, scene = SceneId.PAPER),
                colors = WidgetPalettes.all[6].colors,
                elements = setOf(CITY, DATE, SUNRISE, SUNSET, DAYLIGHT),
                typography = TypographySpec(font = WidgetFont.CORMORANT, weight = FontWeightChoice.BOLD, labelCase = TextCase.TITLE),
            ),
        ),
        WidgetTemplate(
            "pill_next", "Sun Pill", "A slim capsule: icon, next event and countdown.", WidgetSizeClass.SMALL,
            WidgetSpec(
                templateId = "pill_next", name = "Sun Pill", layout = WidgetLayout.NEXT_EVENT,
                shape = WidgetShape.CAPSULE,
                background = BackgroundSpec(type = BackgroundType.GRADIENT, color = c(0xFFFF7A3D), color2 = c(0xFFFFB547), angle = 0f),
                colors = WidgetPalettes.all[0].colors,
                elements = setOf(NEXT_EVENT, ICONS),
                typography = TypographySpec(font = WidgetFont.NUNITO, weight = FontWeightChoice.BOLD),
            ),
        ),
        WidgetTemplate(
            "aurora_ring", "Aurora Ring", "Glowing ring of daylight progress on a dark sky.", WidgetSizeClass.SQUARE,
            WidgetSpec(
                templateId = "aurora_ring", name = "Aurora Ring", layout = WidgetLayout.PROGRESS_RING,
                shape = WidgetShape.RING, background = BackgroundSpec(type = BackgroundType.SCENE, scene = SceneId.AURORA),
                colors = WidgetPalettes.all[10].colors,
                elements = setOf(PROGRESS, SUNRISE, SUNSET),
                typography = TypographySpec(font = WidgetFont.QUICKSAND, weight = FontWeightChoice.BOLD),
            ),
        ),
    )

    fun byId(id: String): WidgetTemplate? = all.firstOrNull { it.id == id }

    val default: WidgetTemplate get() = all[1]

    fun defaultFor(sizeClass: WidgetSizeClass): WidgetTemplate = when (sizeClass) {
        WidgetSizeClass.SMALL -> byId("minimal")!!
        WidgetSizeClass.SQUARE -> byId("solar_dial")!!
        WidgetSizeClass.WIDE -> byId("digital")!!
        WidgetSizeClass.LARGE -> byId("large_solar")!!
    }
}
