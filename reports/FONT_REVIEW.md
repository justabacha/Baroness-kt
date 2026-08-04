# Font Application API Documentation

**Date**: 2026-07-21  
**Feature**: Font System API  
**Reviewer**: Cascade Agent

---

## 1. Active Font Storage

### Format of `activeFont` and `previewFont` StateFlows

**Location**: `SettingsViewModel.kt:28-32`

```kotlin
val activeFont: StateFlow<String> = repository.getFontFlow()
    .stateIn(viewModelScope, SharingStarted.Eagerly, "system")

private val _previewFont = MutableStateFlow("system")
val previewFont: StateFlow<String> = _previewFont.asStateFlow()
```

### Storage Format

The font ID is stored as a **String** with two possible formats:

#### Single-Weight Fonts
Format: `{familyId}`

**Examples**:
- `"yuyu"`
- `"gamaamli"`
- `"matemasie"`
- `"marckscript"`
- `"kaushanscript"`
- `"permanentmarker"`
- `"shadowsintolight"`

#### Multi-Weight Fonts
Format: `{familyId}_{weightId}`

**Examples**:
- `"playfairdisplay_regular"`
- `"playfairdisplay_bold"`
- `"lifesavers_regular"`
- `"lifesavers_bold"`
- `"robotomono_thin"`
- `"dancingscript_medium"`

### How the Format is Constructed

**Location**: `SettingsViewModel.kt:85-87`

```kotlin
fun previewFont(familyId: String, weightId: String? = null) {
    _previewFont.value = if (weightId != null) "${familyId}_$weightId" else familyId
}
```

**Logic**:
- If `weightId` is provided (multi-weight font): stores as `"familyId_weightId"`
- If `weightId` is null (single-weight font): stores as `"familyId"`

### Default Values

**Location**: `SettingsViewModel.kt:29, 66`

```kotlin
// Default in StateFlow initialization
val activeFont: StateFlow<String> = repository.getFontFlow()
    .stateIn(viewModelScope, SharingStarted.Eagerly, "system")

// Default constant for reverting
companion object {
    const val DEFAULT_FONT = "playfairdisplay_regular"
}
```

**Note**: There's an inconsistency - the StateFlow default is `"system"` but the revert constant is `"playfairdisplay_regular"`.

---

## 2. Font Retrieval

### Helper Function Availability

**Status**: ⚠️ **PARTIAL**

There is a helper function to get `FontFamily` by name, but it does **NOT** handle weights.

**Location**: `Type.kt:105-118`

```kotlin
object AppFonts {
    /**
     * Retrieves a FontFamily by its name (case-insensitive).
     */
    fun byName(name: String): FontFamily? = when (name.lowercase()) {
        "yuyu" -> Yuyu
        "lifesavers" -> Lifesavers
        "robotomono" -> RobotoMono
        "gamaamli" -> Gamaamli
        "matemasie" -> Matemasie
        "dancingscript" -> DancingScript
        "marckscript" -> MarckScript
        "playfairdisplay" -> PlayfairDisplay
        "kaushanscript" -> KaushanScript
        "permanentmarker" -> PermanentMarker
        "shadowsintolight" -> ShadowsIntoLight
        else -> null
    }
}
```

**Limitations**:
- Only accepts family name (e.g., `"playfairdisplay"`)
- Does NOT accept full font ID with weight (e.g., `"playfairdisplay_bold"`)
- Does NOT return `FontWeight`
- Returns `null` for unknown names

### No Helper for Full Resolution

**Status**: ❌ **MISSING**

There is **NO** helper function to resolve the full font ID (with weight) to `FontFamily` + `FontWeight`.

**What You Need** (not implemented):
```kotlin
// This function does NOT exist
fun resolveFont(fontId: String): Pair<FontFamily, FontWeight>?
```

### Manual Resolution Pattern

**Location**: `DrawerFont.kt:48-84` (fontFamilies list)

To resolve a font ID manually, you need to:

1. Parse the font ID to extract familyId and weightId
2. Look up the family in the `fontFamilies` list
3. Find the corresponding weight option

**Example from DrawerFont.kt**:
```kotlin
val fontFamilies = listOf(
    FontFamilyOption("playfairdisplay", "Playfair Display", AppFonts.PlayfairDisplay, "P", listOf(
        FontWeightOption("playfairdisplay_regular", "Regular", FontWeight.Normal),
        FontWeightOption("playfairdisplay_bold", "Bold", FontWeight.Bold),
        // ... more weights
    )),
    // ... other families
)

// To resolve:
val fontId = "playfairdisplay_bold"
val familyId = fontId.substringBefore("_") // "playfairdisplay"
val weightId = fontId.substringAfter("_") // "bold"

val family = fontFamilies.find { it.id == familyId }
val weight = family?.weights?.find { it.id == weightId }

val fontFamily = family?.fontFamily // AppFonts.PlayfairDisplay
val fontWeight = weight?.fontWeight // FontWeight.Bold
```

---

## 3. Default Font

### Default Font Definition

**Location**: `SettingsViewModel.kt:66`

```kotlin
companion object {
    const val DEFAULT_FONT = "playfairdisplay_regular"
}
```

**Default Font**: Playfair Display Regular

### Typography Default

**Location**: `Type.kt:122-130`

```kotlin
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
```

**Default Typography Font**: `FontFamily.Default` (system font)

**Inconsistency**: The default font constant is `"playfairdisplay_regular"` but the Typography uses `FontFamily.Default`.

---

## 4. Text Composable Usage

### Current Implementation (Manual Resolution)

**Status**: ⚠️ **NO SIMPLE WAY**

There is **NO** simple helper function. You must manually resolve the font ID.

**Example Pattern** (requires manual implementation):

```kotlin
@Composable
fun MyScreen(viewModel: SettingsViewModel) {
    val activeFontId by viewModel.activeFont.collectAsState()
    
    // Manual resolution (you need to implement this)
    val (fontFamily, fontWeight) = resolveFont(activeFontId)
    
    Text(
        text = "Hello",
        fontFamily = fontFamily,
        fontWeight = fontWeight
    )
}

// You need to implement this helper yourself:
fun resolveFont(fontId: String): Pair<FontFamily?, FontWeight> {
    val familyId = if (fontId.contains("_")) fontId.substringBefore("_") else fontId
    val weightId = if (fontId.contains("_")) fontId.substringAfter("_") else null
    
    val fontFamily = AppFonts.byName(familyId)
    
    // You need to map weightId to FontWeight manually
    val fontWeight = when (weightId) {
        "regular" -> FontWeight.Normal
        "bold" -> FontWeight.Bold
        "extrabold" -> FontWeight.ExtraBold
        // ... map all weights
        else -> FontWeight.Normal
    }
    
    return Pair(fontFamily, fontWeight)
}
```

### Simpler Alternative (Using DrawerFont.kt Pattern)

**Location**: `DrawerFont.kt:48-84`

You can reuse the `fontFamilies` list from `DrawerFont.kt`:

```kotlin
@Composable
fun MyScreen(viewModel: SettingsViewModel) {
    val activeFontId by viewModel.activeFont.collectAsState()
    
    val familyId = if (activeFontId.contains("_")) {
        activeFontId.substringBefore("_")
    } else {
        activeFontId
    }
    
    val weightId = if (activeFontId.contains("_")) {
        activeFontId.substringAfter("_")
    } else {
        null
    }
    
    val family = fontFamilies.find { it.id == familyId }
    val weight = family?.weights?.find { it.id == "${familyId}_$weightId" }
    
    Text(
        text = "Hello",
        fontFamily = family?.fontFamily,
        fontWeight = weight?.fontWeight ?: FontWeight.Normal
    )
}
```

### Recommended Helper Function (Not Implemented)

**This function should be added to `AppFonts` or a new utility:**

```kotlin
object AppFonts {
    // ... existing code
    
    /**
     * Resolves a font ID (with optional weight) to FontFamily and FontWeight.
     * 
     * @param fontId Format: "familyId" or "familyId_weightId"
     * @return Pair of FontFamily and FontWeight, or null if not found
     */
    fun resolve(fontId: String): Pair<FontFamily, FontWeight>? {
        if (fontId == "system") {
            return Pair(FontFamily.Default, FontWeight.Normal)
        }
        
        val familyId = if (fontId.contains("_")) fontId.substringBefore("_") else fontId
        val weightId = if (fontId.contains("_")) fontId.substringAfter("_") else null
        
        val fontFamily = byName(familyId) ?: return null
        
        val fontWeight = when (weightId) {
            "regular", null -> FontWeight.Normal
            "italic" -> FontWeight.Normal
            "medium" -> FontWeight.Medium
            "semibold" -> FontWeight.SemiBold
            "bold" -> FontWeight.Bold
            "extrabold" -> FontWeight.ExtraBold
            "black", "blackitalic" -> FontWeight.Black
            "thin" -> FontWeight.Thin
            "light" -> FontWeight.Light
            else -> FontWeight.Normal
        }
        
        return Pair(fontFamily, fontWeight)
    }
}
```

**Usage with Helper**:
```kotlin
@Composable
fun MyScreen(viewModel: SettingsViewModel) {
    val activeFontId by viewModel.activeFont.collectAsState()
    val (fontFamily, fontWeight) = AppFonts.resolve(activeFontId) ?: Pair(FontFamily.Default, FontWeight.Normal)
    
    Text(
        text = "Hello",
        fontFamily = fontFamily,
        fontWeight = fontWeight
    )
}
```

---

## 5. Typography Integration

### Current Status

**Status**: ❌ **NOT INTEGRATED**

The font system is **NOT** integrated with Material3 Typography.

**Evidence**: `Type.kt:122-130`

```kotlin
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,  // Always uses system font
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
```

### Manual Application Required

**You must manually set `fontFamily` on every Text composable.**

**Example**:
```kotlin
// Typography is NOT updated with active font
// You must do this manually:

Text(
    text = "Hello",
    fontFamily = AppFonts.PlayfairDisplay,  // Manual
    fontWeight = FontWeight.Bold           // Manual
)
```

### No Dynamic Typography

There is **NO** mechanism to update the Material3 Typography based on the active font.

**What's Missing**:
- No `Typography` update when font changes
- No `ProvideTextStyle` or `ProvideTypography` integration
- No theme-level font application

---

## 6. Headings vs Body Text

### Multi-Weight Font Families

**Location**: `Type.kt`

Fonts with multiple weights have all weights defined in the `FontFamily`:

**PlayfairDisplay** (lines 59-72):
```kotlin
val PlayfairDisplayFont = FontFamily(
    Font(R.font.playfairdisplay_regular, FontWeight.Normal),
    Font(R.font.playfairdisplay_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.playfairdisplay_medium, FontWeight.Medium),
    Font(R.font.playfairdisplay_mediumitalic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.playfairdisplay_semibold, FontWeight.SemiBold),
    Font(R.font.playfairdisplay_semibolditalic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.playfairdisplay_bold, FontWeight.Bold),
    Font(R.font.playfairdisplay_bolditalic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.playfairdisplay_extrabold, FontWeight.ExtraBold),
    Font(R.font.playfairdisplay_extrabolditalic, FontWeight.ExtraBold, FontStyle.Italic),
    Font(R.font.playfairdisplay_black, FontWeight.Black),
    Font(R.font.playfairdisplay_blackitalic, FontWeight.Black, FontStyle.Italic)
)
```

**RobotoMono** (lines 23-38):
```kotlin
val RobotoMonoFont = FontFamily(
    Font(R.font.robotomono_thin, FontWeight.Thin),
    Font(R.font.robotomono_thinitalic, FontWeight.Thin, FontStyle.Italic),
    Font(R.font.robotomono_extralight, FontWeight.ExtraLight),
    // ... more weights
    Font(R.font.robotomono_bold, FontWeight.Bold),
    Font(R.font.robotomono_bolditalic, FontWeight.Bold, FontStyle.Italic)
)
```

### How to Make Headings Bold

**If you apply "PlayfairDisplay Regular":**

```kotlin
// Active font: "playfairdisplay_regular"
val (fontFamily, fontWeight) = AppFonts.resolve("playfairdisplay_regular")

// For body text (regular weight)
Text(
    text = "Body text",
    fontFamily = fontFamily,  // PlayfairDisplay
    fontWeight = fontWeight    // Normal
)

// For headings (bold weight)
Text(
    text = "Heading",
    fontFamily = fontFamily,  // PlayfairDisplay (same family)
    fontWeight = FontWeight.Bold  // Use Bold weight from the same family
)
```

**Key Point**: You use the **same `FontFamily`** but change the `FontWeight` to access different weights within that family.

### What Happens If Weight Doesn't Exist

**Compose Behavior**: Compose will fall back to the closest available weight.

**Example with Single-Weight Font**:
```kotlin
// Active font: "yuyu" (only has Normal weight)
val (fontFamily, fontWeight) = AppFonts.resolve("yuyu")

Text(
    text = "Heading",
    fontFamily = fontFamily,  // Yuyu
    fontWeight = FontWeight.Bold  // Yuyu doesn't have Bold
)

// Result: Compose will use the closest weight (Normal)
// The text will NOT appear bold
```

**Example with Multi-Weight Font**:
```kotlin
// Active font: "playfairdisplay_regular"
val (fontFamily, fontWeight) = AppFonts.resolve("playfairdisplay_regular")

Text(
    text = "Heading",
    fontFamily = fontFamily,  // PlayfairDisplay
    fontWeight = FontWeight.Black  // PlayfairDisplay has Black weight
)

// Result: Text will appear in Black weight (very bold)
```

### Weight Availability by Font

**Multi-Weight Fonts** (support headings with different weights):
- Lifesavers: Regular, Bold, ExtraBold
- RobotoMono: Thin, ExtraLight, Light, Regular, Medium, SemiBold, Bold
- DancingScript: Regular, Medium, SemiBold, Bold
- PlayfairDisplay: Regular, Medium, SemiBold, Bold, ExtraBold, Black

**Single-Weight Fonts** (cannot change weight):
- Yuyu: Regular only
- Gamaamli: Regular only
- Matemasie: Regular only
- MarckScript: Regular only
- KaushanScript: Regular only
- PermanentMarker: Regular only
- ShadowsIntoLight: Regular only

---

## Summary

### Current State

| Aspect | Status | Notes |
|--------|--------|-------|
| **Font Storage** | ✅ Working | String format: "familyId" or "familyId_weightId" |
| **Font Retrieval** | ⚠️ Partial | `AppFonts.byName()` exists but no weight resolution |
| **Helper Function** | ❌ Missing | No `resolveFont()` helper for full ID resolution |
| **Default Font** | ⚠️ Inconsistent | DEFAULT_FONT = "playfairdisplay_regular" but Typography uses system |
| **Typography Integration** | ❌ Not Integrated | Must manually set fontFamily on every Text |
| **Headings Support** | ✅ Working | Multi-weight fonts support different weights |

### Recommended Implementation

**Add this helper to `AppFonts` in `Type.kt`:**

```kotlin
object AppFonts {
    // ... existing code
    
    /**
     * Resolves a font ID (with optional weight) to FontFamily and FontWeight.
     * 
     * @param fontId Format: "familyId" or "familyId_weightId" or "system"
     * @return Pair of FontFamily and FontWeight, or null if not found
     */
    fun resolve(fontId: String): Pair<FontFamily, FontWeight>? {
        if (fontId == "system") {
            return Pair(FontFamily.Default, FontWeight.Normal)
        }
        
        val familyId = if (fontId.contains("_")) fontId.substringBefore("_") else fontId
        val weightId = if (fontId.contains("_")) fontId.substringAfter("_") else null
        
        val fontFamily = byName(familyId) ?: return null
        
        val fontWeight = when (weightId) {
            "regular", null -> FontWeight.Normal
            "italic" -> FontWeight.Normal
            "medium" -> FontWeight.Medium
            "semibold" -> FontWeight.SemiBold
            "bold" -> FontWeight.Bold
            "extrabold" -> FontWeight.ExtraBold
            "black", "blackitalic" -> FontWeight.Black
            "thin" -> FontWeight.Thin
            "light" -> FontWeight.Light
            "extralight" -> FontWeight.ExtraLight
            else -> FontWeight.Normal
        }
        
        return Pair(fontFamily, fontWeight)
    }
}
```

### Usage Example

```kotlin
@Composable
fun MyScreen(viewModel: SettingsViewModel) {
    val activeFontId by viewModel.activeFont.collectAsState()
    val (fontFamily, fontWeight) = AppFonts.resolve(activeFontId) ?: Pair(FontFamily.Default, FontWeight.Normal)
    
    // Body text
    Text(
        text = "Body text",
        fontFamily = fontFamily,
        fontWeight = fontWeight
    )
    
    // Heading (bold)
    Text(
        text = "Heading",
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold
    )
}
```

---

**Report End**
