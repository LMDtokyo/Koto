# Koto SVG icons

Drop SVG files here and reference them from Compose as:

```kotlin
Icon(
    painter            = svgIcon("my_icon"),
    contentDescription = null,
    tint               = KotoTheme.colors.accent,
    modifier           = Modifier.size(22.dp),
)
```

## Rules

- **Name**: `snake_case.svg`, ASCII only, no spaces.
- **Tintable**: must be monochrome (single fill color, no gradients).
  - The `tint` in `Icon(...)` replaces all opaque pixels with the theme color.
  - If you want to preserve source colors (e.g. brand marks), use `Image(painter = svgIcon(...))`.
- **Size**: authored viewBox is preserved; callers pass `Modifier.size(...)`.
- **Stroke vs fill**: either works. For stroke-based, use `stroke="currentColor"` so the tint applies.

## Where this sits vs `KotoIcons`

| | `KotoIcons.Back` (ImageVector) | `svgIcon("back")` (SVG file) |
|---|---|---|
| Storage | compiled Kotlin | classpath resource |
| Iteration | edit Kotlin, rebuild | drop file, reload |
| Multi-color | no | yes |
| Hot-path glyphs | **use this** | overkill |
| Brand / custom | awkward | **use this** |

Both APIs coexist; pick the one that matches the glyph's complexity and
iteration speed needs.

## Authoring tip

Most vector tools (Figma, Illustrator, Sketch, Affinity) export SVG with
extra metadata (`<title>`, `<desc>`, XMLNS comments). That's fine — Skia
ignores it. What matters: keep `viewBox`, keep paths, remove embedded raster.
