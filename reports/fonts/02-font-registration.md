# Font Registration — Implementation Record

## Context

Following the font folder cleanup (`reports/fonts/01-font-folder-cleanup.md`), 24 new font files were flattened into `app/src/main/res/font/`.

This implementation phase registered the 24 new fonts in `AppFonts` (`ui/theme/Type.kt`) and `FONT_REGISTRY` (`data/QuoteMarkup.kt`), reshaped the user-facing appearance font picker `fontFamilies` (`components/FontOptions.kt`), and verified that saved font selection fallback operates safely without crashing.

---

## 1. `AppFonts` Declarations (`ui/theme/Type.kt`)

Declared 24 new `FontFamily` top-level variables and registered them as properties on `object AppFonts` along with lower-case case-insensitive lookup entries in `AppFonts.byName(name)`:

| Font Name | `FontFamily` Variable | `AppFonts` Property | Resource File |
| :--- | :--- | :--- | :--- |
| Christmas Bundle | `ChristmasBundleFont` | `AppFonts.ChristmasBundle` | `R.font.christmas_bundle` |
| Christmas Dariona | `ChristmasDarionaFont` | `AppFonts.ChristmasDariona` | `R.font.christmas_dariona` |
| Cookie Monster | `CookieMonsterFont` | `AppFonts.CookieMonster` | `R.font.cookie_monster` |
| Crayon Hand | `CrayonHandFont` | `AppFonts.CrayonHand` | `R.font.crayon_hand` |
| Evil Scratch | `EvilScratchFont` | `AppFonts.EvilScratch` | `R.font.evil_scratch` |
| Filx Girl | `FilxGirlFont` | `AppFonts.FilxGirl` | `R.font.filx_girl` |
| God of War | `GodOfWarFont` | `AppFonts.GodOfWar` | `R.font.god_of_war` |
| Game of Squids | `GameOfSquidsFont` | `AppFonts.GameOfSquids` | `R.font.game_of_squids` |
| Graveyard Crimes | `GraveyardCrimesFont` | `AppFonts.GraveyardCrimes` | `R.font.graveyard_crimes` |
| Happy Birthday | `HappyBirthdayFont` | `AppFonts.HappyBirthday` | `R.font.happy_birthday` |
| Harukaze | `HarukazeFont` | `AppFonts.Harukaze` | `R.font.harukaze` |
| Hello Christmas | `HelloChristmasFont` | `AppFonts.HelloChristmas` | `R.font.hello_christmas` |
| Hoshiko Satsuki | `HoshikoSatsukiFont` | `AppFonts.HoshikoSatsuki` | `R.font.hoshiko_satsuki` |
| Jealousy | `JealousyFont` | `AppFonts.Jealousy` | `R.font.jealousy` |
| Klomang Brush | `KlomangBrushFont` | `AppFonts.KlomangBrush` | `R.font.klomang_brush` |
| Power Skate | `PowerSkateFont` | `AppFonts.PowerSkate` | `R.font.power_skate` |
| Racing in the Rain | `RacingInTheRainFont` | `AppFonts.RacingInTheRain` | `R.font.racing_in_the_rain` |
| Sakelar | `SakelarFont` | `AppFonts.Sakelar` | `R.font.sakelar` |
| Shareit | `ShareitFont` | `AppFonts.Shareit` | `R.font.shareit` |
| Spell of Asia | `SpellOfAsiaFont` | `AppFonts.SpellOfAsia` | `R.font.spell_of_asia` |
| Spookery | `SpookeryFont` | `AppFonts.Spookery` | `R.font.spookery` |
| Sprucken | `SpruckenFont` | `AppFonts.Sprucken` | `R.font.sprucken` |
| The Rules of the Game | `TheRulesOfTheGameFont` | `AppFonts.TheRulesOfTheGame` | `R.font.the_rules_of_the_game` |
| Trash Secret | `TrashSecretFont` | `AppFonts.TrashSecret` | `R.font.trash_secret` |

---

## 2. User Picker Reshaping (`components/FontOptions.kt`)

### 2.1 Removed 6 Existing Fonts
Removed from the user-facing `fontFamilies` selection list (retained in `AppFonts` and `FONT_REGISTRY` for quote markup):
1. `Yuyu` (`yuyu`)
2. `Matemasie` (`matemasie`)
3. `DancingScript` (`dancingscript`)
4. `MarckScript` (`marckscript`)
5. `PermanentMarker` (`permanentmarker`)
6. `ShadowsIntoLight` (`shadowsintolight`)

### 2.2 Added 8 New Fonts
Added to `fontFamilies` selection list:
1. `Game of Squids` (`game_of_squids` -> `AppFonts.GameOfSquids`)
2. `Shareit` (`shareit` -> `AppFonts.Shareit`)
3. `Hello Christmas` (`hello_christmas` -> `AppFonts.HelloChristmas`)
4. `Hoshiko Satsuki` (`hoshiko_satsuki` -> `AppFonts.HoshikoSatsuki`)
5. `Sakelar` (`sakelar` -> `AppFonts.Sakelar`)
6. `Spell of Asia` (`spell_of_asia` -> `AppFonts.SpellOfAsia`)
7. `Cookie Monster` (`cookie_monster` -> `AppFonts.CookieMonster`)
8. `Filx Girl` (`filx_girl` -> `AppFonts.FilxGirl`)

**Total Active User Fonts**: 13 families (`Lifesavers`, `RobotoMono`, `Gamaamli`, `PlayfairDisplay`, `KaushanScript`, plus the 8 newly added families).

---

## 3. Quote Markup Registry Additions (`data/QuoteMarkup.kt`)

Extended `FONT_REGISTRY` map with 24 new tag mappings while preserving all 11 original tag mappings:

```kotlin
        // 11 Original Fonts
        "yuyu" to AppFonts.Yuyu,
        "lifesavers" to AppFonts.Lifesavers,
        "robotomono" to AppFonts.RobotoMono,
        "gamaamli" to AppFonts.Gamaamli,
        "matemasie" to AppFonts.Matemasie,
        "dancing" to AppFonts.DancingScript,
        "marck" to AppFonts.MarckScript,
        "playfair" to AppFonts.PlayfairDisplay,
        "kaushan" to AppFonts.KaushanScript,
        "marker" to AppFonts.PermanentMarker,
        "shadows" to AppFonts.ShadowsIntoLight,

        // New Fonts
        "xmas_bundle" to AppFonts.ChristmasBundle,
        "xmas_dariona" to AppFonts.ChristmasDariona,
        "cookie" to AppFonts.CookieMonster,
        "crayon" to AppFonts.CrayonHand,
        "evil" to AppFonts.EvilScratch,
        "filx" to AppFonts.FilxGirl,
        "god" to AppFonts.GodOfWar,
        "squids" to AppFonts.GameOfSquids,
        "graveyard" to AppFonts.GraveyardCrimes,
        "birthday" to AppFonts.HappyBirthday,
        "harukaze" to AppFonts.Harukaze,
        "hello_xmas" to AppFonts.HelloChristmas,
        "hoshiko" to AppFonts.HoshikoSatsuki,
        "jealousy" to AppFonts.Jealousy,
        "klomang" to AppFonts.KlomangBrush,
        "skate" to AppFonts.PowerSkate,
        "rain" to AppFonts.RacingInTheRain,
        "sakelar" to AppFonts.Sakelar,
        "shareit" to AppFonts.Shareit,
        "asia" to AppFonts.SpellOfAsia,
        "spookery" to AppFonts.Spookery,
        "sprucken" to AppFonts.Sprucken,
        "rules" to AppFonts.TheRulesOfTheGame,
        "trash" to AppFonts.TrashSecret
```

**Total Unique Quote Tags**: 35 tag mappings with zero collisions.

---

## 4. Saved Font Preference Fallback Analysis

In `FontSettingsPage.kt` (lines 48–56) and `AppFonts.resolve()`:
- `FontSettingsPage.kt` resolves selected family via `fontFamilies.find { it.id == familyId } ?: fontFamilies.find { it.id == "playfairdisplay" } ?: fontFamilies.first()`.
- `AppFonts.resolve(fontId)` queries `AppFonts.byName(familyId)`.
- If a user previously selected a font removed from the user picker (e.g. `"dancingscript"`), `AppFonts.resolve()` still resolves `DancingScript` for rendering, and `FontSettingsPage.kt` falls back safely to `PlayfairDisplay` if opened without crashing.
- No additional fallback code was required as robust fallback chains already existed.

---

## 5. Scope & Preservation Confirmation

- **Physical Font Files**: Zero physical files in `app/src/main/res/font/` were moved, renamed, or modified.
- **Other Code**: No screens, ViewModels, or data stores outside `Type.kt`, `FontOptions.kt`, and `QuoteMarkup.kt` were modified.

---

## 6. Build Result

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

---

## 7. Manual Verification Checklist

- [ ] Force-stop. Cold start. Open Settings → Appearance → Font. The user picker shows 13 font options (8 new, 6 removed fonts absent).
- [ ] Select a new font (e.g. "Game of Squids"). Return to Dashboard. App-wide typography updates.
- [ ] Open Settings → Appearance → Font again. Selection persists as "Game of Squids".
- [ ] Return to Dashboard. Quote cards render with app-wide active font while quote inline markup (`<kaushan>`, `<playfair>`, etc.) renders with its specific font.
- [ ] Test new quote tag in `SignatureLoops.kt` (e.g. `<squids>test</>`). Confirm tag renders in Game of Squids font.
- [ ] Test retired font quote tag in `SignatureLoops.kt` (e.g. `<marker>test</>`). Confirm tag continues rendering in Permanent Marker.
- [ ] Revert any temporary changes in `SignatureLoops.kt`.

## Post-Review — Selection Pill Fix

### 1. Diagnosis & Root Cause

**Cause A (Family ID Mismatch & Underscore Parsing Error)**:
In `FontSettingsPage.kt`:
```kotlin
val selectedFamily = remember(previewFontId) {
    val familyId = if (previewFontId.contains("_")) previewFontId.substringBefore("_") else previewFontId
    fontFamilies.find { it.id == familyId } ?: fontFamilies.find { it.id == "playfairdisplay" } ?: fontFamilies.first()
}
```

- **Mechanism**: `FontSettingsPage.kt` parsed `familyId` from `previewFontId` by calling `substringBefore("_")` under the assumption that family IDs never contained underscores (since the 11 original font family IDs were single-word alphanumeric strings like `playfairdisplay`, `robotomono`, `gamaamli`).
- **Failure**: When tapping a newly added font (e.g. `Game of Squids` with ID `game_of_squids`), `previewFontId.substringBefore("_")` evaluated to `"game"`. `fontFamilies.find { it.id == "game" }` returned `null`, causing `selectedFamily` to fall back to `PlayfairDisplay`.
- **Symptom**: Tapping a new font updated the live preview and applying worked, but `isFamilySelected` (`selectedFamily.id == family.id`) evaluated to `false` for the tapped card, causing the selection pill (background highlight & border) to remain on `PlayfairDisplay`.

---

## 2. Fix Applied

1. **`FontOptions.kt`**:
   Standardized all 13 family IDs in `fontFamilies` to single-word lower-case IDs without underscores, matching the pattern of the original entries (`gameofsquids`, `hellochristmas`, `hoshikosatsuki`, `spellofasia`, `cookiemonster`, `filxgirl`).

2. **`FontSettingsPage.kt`**:
   Updated family and weight resolution to be robust against any ID format:
   ```kotlin
       val selectedFamily = remember(previewFontId) {
           fontFamilies.find { previewFontId == it.id || previewFontId.startsWith("${it.id}_") }
               ?: fontFamilies.find { it.id == "playfairdisplay" }
               ?: fontFamilies.first()
       }

       val selectedWeight = remember(previewFontId, selectedFamily) {
           if (previewFontId.startsWith("${selectedFamily.id}_")) {
               val weightId = previewFontId.removePrefix("${selectedFamily.id}_")
               selectedFamily.weights.find { it.id == weightId }
           } else {
               selectedFamily.weights.firstOrNull()
           }
       }
   ```

---

## 3. Scope & Preservation Confirmation

- **Modified Files**: Strictly limited to `components/FontOptions.kt` and `screens/settings/FontSettingsPage.kt`.
- **`VerilySerifMono` Status**: Confirmed `VerilySerifMono` was **not added back** (retained at 24 newly registered fonts).
- **Physical Font Files**: Unmodified.

---

## 4. Build Result

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

---

## 5. Manual Verification Checklist

- [ ] Open Settings → Appearance → Font.
- [ ] Tap "Game of Squids". Selection pill moves to "Game of Squids" immediately.
- [ ] Tap "Shareit". Selection pill moves to "Shareit".
- [ ] Tap an original font like "Playfair Display". Selection pill moves to "Playfair Display".
- [ ] Tap back to "Hello Christmas". Selection pill moves to "Hello Christmas".
- [ ] Tap APPLY. The selected font applies app-wide.
- [ ] Cold start. The applied font displays with the selection pill on when the font picker is reopened.

