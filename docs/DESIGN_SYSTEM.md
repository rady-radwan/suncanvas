# SunCanvas design system

Calm, premium and focused on light. The interface should feel like looking at the sky: dark navy nights, warm white days, golden light as the accent.

## Color

| Token | Dark | Light | Use |
|---|---|---|---|
| Background | `#0B1026` deep navy | `#FFF9F2` warm white | App background |
| Surface container | `#10173A` | `#FBF1E4` | Cards |
| Primary — golden amber | `#FFB547` | `#B86A00` | Main actions, highlights |
| Secondary — sunset orange | `#FF7A3D` | `#C94F1C` | Sunset, secondary emphasis |
| Tertiary — soft blue | `#7FB2FF` | `#3D6FD1` | Night, blue hour, charts |
| Twilight | `#8E6CFF` | `#6B4FD8` | Twilight bands |
| On-surface muted | `#A9B1CF` | `#6E6052` | Labels, captions |

Semantic sun colors (`LocalSunColors`): `sunrise`, `sunset`, `golden`, `blue`, `twilight`, `night`.

Theme modes: Light, Dark, Follow system, and **Automatic by sunrise/sunset** at the active location.

## Typography

* **Display — Space Grotesk** (Light for clocks, Medium for headlines): characterful digits that look great at large sizes.
* **Text — Inter** (Regular/Medium/SemiBold): highly legible UI text.
* Clock: Space Grotesk Light 72 sp, −1 sp tracking.
* Labels: Inter Medium, uppercase in section headers.

## Shape & spacing

* Corner radii: 8 / 12 / 20 / 28 / 36 dp (Material 3 shape scale). Cards use 20 dp.
* 4 dp grid: 4, 8, 12, 16, 24, 32. Screen margins 16–20 dp.
* Cards are tonal (container colors), not shadowed, for a quiet, flat look.

## Iconography

Material Symbols Rounded for UI, plus custom sunrise/sunset/sun/moon glyphs drawn in code so the app and widgets share the exact same icons.

## Motion

Subtle fades between destinations, animated visibility for contextual notices. Clocks tick once per second; dashboard data recomputes once per minute.

## Accessibility

* Content descriptions on every chart, clock, arc and icon button; headings marked for TalkBack.
* Minimum 48 dp touch targets (Material defaults).
* Text contrast follows WCAG; the widget editor shows a **contrast warning** below 3:1 against light and dark wallpapers.
* Layouts scroll and reflow for small screens and large font sizes.

## Widget visual language

21 starting templates, 12 palettes, 11 bundled fonts (Inter, Nunito, Poppins, Space Grotesk, Playfair Display, Cormorant Garamond, JetBrains Mono, Orbitron, Oswald, Bebas Neue, Quicksand — all OFL), 12 shapes, 12 background types including 8 procedural scenes (horizon, mountains, dunes, ocean, starfield, aurora, city skyline, warm paper) and a "live sky" that follows the Sun's elevation.

## Logo

The mark combines a clock ring, a rising sun and a horizon swoosh. The refined version (`branding/suncanvas-icon.svg`) simplifies the original into clean geometric strokes, keeps everything inside the adaptive-icon safe zone, adds a monochrome layer for Android 13 themed icons, and uses consistent stroke weights so it stays crisp at small sizes.
