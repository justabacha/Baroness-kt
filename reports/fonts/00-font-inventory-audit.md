# Font Inventory Audit — Existing and Newly Downloaded Report

## 1. Executive Summary

This audit catalogs all physical font files in `app/src/main/res/font/` and `app/src/main/res/font/NewFonts/`, evaluates their registration status across `AppFonts`, `fontFamilies`, and `QuoteMarkup.FONT_REGISTRY`, and checks `.otf` compatibility against the project's `minSdk` (24).

- **Total Physical Font Files**: 74 files (49 in root `res/font/`, 25 in `res/font/NewFonts/`).
- **Registered Families**: 11 families registered symmetrically across `AppFonts`, `fontFamilies`, and `QuoteMarkup.FONT_REGISTRY`.
- **Orphan Files**: 6 static/variable font variants in `res/font/` and 25 files in `NewFonts/` are currently unreferenced by Kotlin code.
- **`.otf` Compatibility Concern**: The project `minSdk` is **24**, whereas native `.otf` font resource loading in `res/font/` requires **API 26+**. Placing `.otf` files in `res/font/` will crash devices on API 24–25 unless converted to `.ttf` or isolated in `res/font-v26/`.

---

## 2. Physical File Inventory (Section 1)

### 2.1 Root Font Directory (`app/src/main/res/font/`)
Contains 49 files, all `.ttf` extensions.

| Filename | Extension | Size / Type | Registration Status |
| :--- | :--- | :--- | :--- |
| `dancingscript_bold.ttf` | .ttf | Static Weight | Registered (`DancingScript`) |
| `dancingscript_medium.ttf` | .ttf | Static Weight | Registered (`DancingScript`) |
| `dancingscript_regular.ttf` | .ttf | Static Weight | Registered (`DancingScript`) |
| `dancingscript_semibold.ttf` | .ttf | Static Weight | Registered (`DancingScript`) |
| `dancingscript_variablefont_wght.ttf` | .ttf | Variable Font | **Orphan** |
| `gamaamli_regular.ttf` | .ttf | Static Weight | Registered (`Gamaamli`) |
| `kaushanscript_regular.ttf` | .ttf | Static Weight | Registered (`KaushanScript`) |
| `lifesavers_bold.ttf` | .ttf | Static Weight | Registered (`Lifesavers`) |
| `lifesavers_extrabold.ttf` | .ttf | Static Weight | Registered (`Lifesavers`) |
| `lifesavers_regular.ttf` | .ttf | Static Weight | Registered (`Lifesavers`) |
| `marckscript_regular.ttf` | .ttf | Static Weight | Registered (`MarckScript`) |
| `matemasie_regular.ttf` | .ttf | Static Weight | Registered (`Matemasie`) |
| `permanentmarker_regular.ttf` | .ttf | Static Weight | Registered (`PermanentMarker`) |
| `playfairdisplay_black.ttf` ... (13 Playfair static files) | .ttf | Static Weights | Registered (`PlayfairDisplay`) |
| `playfairdisplay_variablefont_wght.ttf` | .ttf | Variable Font | **Orphan** |
| `playfairdisplay_italic_variablefont_wght.ttf` | .ttf | Variable Font | **Orphan** |
| `robotomono_bold.ttf` ... (16 RobotoMono static files) | .ttf | Static Weights | Registered (`RobotoMono`) |
| `robotomono_variablefont_wght.ttf` | .ttf | Variable Font | **Orphan** |
| `robotomono_italic_variablefont_wght.ttf` | .ttf | Variable Font | **Orphan** |
| `shadowsintolight_regular.ttf` | .ttf | Static Weight | Registered (`ShadowsIntoLight`) |
| `yuyu_regular.ttf` | .ttf | Static Weight | Registered (`Yuyu`) |

### 2.2 NewFonts Directory (`app/src/main/res/font/NewFonts/`)
Contains 25 newly added files (8 `.otf`, 16 `.ttf`, 1 `.TTF`).

| Filename | Extension | Status |
| :--- | :--- | :--- |
| `Christmas Bundle.otf` | .otf | **Unregistered / Orphan** |
| `Christmas Dariona Italic Demo.ttf` | .ttf | **Unregistered / Orphan** |
| `CookieMonster.ttf` | .ttf | **Unregistered / Orphan** |
| `CrayonHandRegular2016Demo.ttf` | .ttf | **Unregistered / Orphan** |
| `Evil Scratch.ttf` | .ttf | **Unregistered / Orphan** |
| `Filxgirl.TTF` | .TTF | **Unregistered / Orphan** |
| `GODOFWAR.TTF` | .TTF | **Unregistered / Orphan** |
| `Game Of Squids.ttf` | .ttf | **Unregistered / Orphan** |
| `Graveyard Crimes.otf` | .otf | **Unregistered / Orphan** |
| `HappyBirthday.ttf` | .ttf | **Unregistered / Orphan** |
| `Harukaze.ttf` | .ttf | **Unregistered / Orphan** |
| `HelloChristmas-1Ge70.ttf` | .ttf | **Unregistered / Orphan** |
| `Hoshiko Satsuki.ttf` | .ttf | **Unregistered / Orphan** |
| `Jealousy Demo.ttf` | .ttf | **Unregistered / Orphan** |
| `Klomang Brush Demo.otf` | .otf | **Unregistered / Orphan** |
| `Power Skate Demo.otf` | .otf | **Unregistered / Orphan** |
| `Racing in the Rain.ttf` | .ttf | **Unregistered / Orphan** |
| `Sakelar.otf` | .otf | **Unregistered / Orphan** |
| `Shareit.ttf` | .ttf | **Unregistered / Orphan** |
| `Spell of Asia.ttf` | .ttf | **Unregistered / Orphan** |
| `Spookery.ttf` | .ttf | **Unregistered / Orphan** |
| `Sprucken_PERSONAL_USE_ONLY.otf` | .otf | **Unregistered / Orphan** |
| `The Rules of the Game.ttf` | .ttf | **Unregistered / Orphan** |
| `Trash Secret DEMO.ttf` | .ttf | **Unregistered / Orphan** |
| `VerilySerifMono.otf` | .otf | **Unregistered / Orphan** |

---

## 3. Extension Analysis (Section 2)

- **`res/font/`**: 49 `.ttf` files. (0 `.otf` files).
- **`res/font/NewFonts/`**: 16 `.ttf` files, 1 `.TTF` file, and 8 `.otf` files.
- **Current `.otf` Usage**: `.otf` files are **not** currently used anywhere in the compiled project code (`AppFonts`, `fontFamilies`, and `QuoteMarkup` reference `.ttf` files exclusively).

---

## 4. Existing Registration Inventory (Section 3)

All 11 registered font families are **fully symmetric** across `AppFonts` (`ui/theme/Type.kt`), `fontFamilies` (`components/FontOptions.kt`), and `QuoteMarkup.FONT_REGISTRY` (`data/QuoteMarkup.kt`):

1. `Yuyu` (`yuyu`)
2. `Lifesavers` (`lifesavers`)
3. `RobotoMono` (`robotomono`)
4. `Gamaamli` (`gamaamli`)
5. `Matemasie` (`matemasie`)
6. `DancingScript` (`dancingscript` / `"dancing"` tag)
7. `MarckScript` (`marckscript` / `"marck"` tag)
8. `PlayfairDisplay` (`playfairdisplay` / `"playfair"` tag)
9. `KaushanScript` (`kaushanscript` / `"kaushan"` tag)
10. `PermanentMarker` (`permanentmarker` / `"marker"` tag)
11. `ShadowsIntoLight` (`shadowsintolight` / `"shadows"` tag)

---

## 5. Referenced-but-Missing and Orphan Files (Section 4)

- **Referenced-but-Missing**: None. Every `R.font.*` resource referenced in `Type.kt` has a corresponding physical `.ttf` file in `res/font/`.
- **Orphans in `res/font/`**: Variable font files (`dancingscript_variablefont_wght.ttf`, `playfairdisplay_variablefont_wght.ttf`, `playfairdisplay_italic_variablefont_wght.ttf`, `robotomono_variablefont_wght.ttf`, `robotomono_italic_variablefont_wght.ttf`, etc.) are present in res/font but unreferenced by `AppFonts` (which uses static weight `Font(...)` declarations).
- **Orphans in `NewFonts/`**: All 25 files are unreferenced.

---

## 6. `.otf` Compatibility Check (Section 6)

- **Android Native Support**: Native loading of `.otf` font files from `res/font/` via `Font(resId)` requires **API 26 (Android 8.0)** or higher.
- **Project `minSdk`**: Configured as **24** (`app/build.gradle.kts`).
- **Safety Assessment**: Using `.otf` files directly in `res/font/` without SDK version qualification (`res/font-v26/`) or conversion to `.ttf` will cause resource inflation crashes on devices running Android 7.0 / 7.1 (API 24/25).
- **Recommendation**: Either convert `.otf` files to `.ttf` before registering, or place them under `res/font-v26/` if targeting API 26+ devices.

---

## 7. Open Questions for the Operator

1. **`.otf` Migration Strategy**: Should newly selected fonts from `NewFonts/` be converted to `.ttf` format to maintain API 24 compatibility across the app, or should `res/font-v26/` be utilized?
2. **Variable Font Cleanup**: Should unreferenced variable font `.ttf` files in `res/font/` be pruned to reduce APK footprint?
