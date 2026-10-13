# Dashboard Quote Card — Black Frame Fix Implementation Record

## Context

The quote card audit (`reports/screens/06-quote-card-flicker-audit.md`) identified that `QuoteCard.kt` applied an explicit `Color.Black` background to the card container `Box`. During Coil's 1–3 frame image decode window, before the painter renders content, the black container background showed through, producing a brief black flash before the quote photo appeared.

This implementation changes the container and empty-state placeholder backgrounds to a neutral dark surface color (`Color(0xFF1E1E1E)`), eliminating the black flash during image decoding.

---

## 1. Card Container Background Change

In `app/src/main/java/com/baroness/app/components/QuoteCard.kt` (line 77):

**Before:**
```kotlin
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .rotate(tiltDeg)
                .shadow(
                    elevation = 35.dp,
                    shape = RoundedCornerShape(15.dp),
                    ambientColor = Color.Black.copy(alpha = 0.7f),
                    spotColor = Color.Black.copy(alpha = 0.7f)
                )
                .clip(RoundedCornerShape(15.dp))
                .border(1.dp, Color.White.copy(alpha = 0.95f), RoundedCornerShape(15.dp))
                .background(Color.Black)
        )
```

**After:**
```kotlin
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .rotate(tiltDeg)
                .shadow(
                    elevation = 35.dp,
                    shape = RoundedCornerShape(15.dp),
                    ambientColor = Color.Black.copy(alpha = 0.7f),
                    spotColor = Color.Black.copy(alpha = 0.7f)
                )
                .clip(RoundedCornerShape(15.dp))
                .border(1.dp, Color.White.copy(alpha = 0.95f), RoundedCornerShape(15.dp))
                .background(Color(0xFF1E1E1E))
        )
```

---

## 2. Empty-State Placeholder Background Change

In `app/src/main/java/com/baroness/app/components/QuoteCard.kt` (line 101):

**Before:**
```kotlin
            } else {
                // Placeholder when no image
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray)
                )
            }
```

**After:**
```kotlin
            } else {
                // Placeholder when no image
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E))
                )
            }
```

Both loading/decode and missing-image states now share the same neutral dark surface background (`#1E1E1E`).

---

## 3. Scope & Preservation Confirmation

- **No Other Files Modified**: Confirmed that `DashboardScreen.kt`, `QuoteRepository.kt`, `DashboardViewModel.kt`, pre-warm routines, and Coil `ImageRequest` configurations were untouched.
- **Card Modifiers & Overlay**: The translucent dark scrim overlay (`Color.Black.copy(alpha = 0.4f)`), card shadows, borders, rotation, typography, and layout remain 100% intact.

---

## 4. Build Result

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

---

## 5. Manual Verification Checklist

- [ ] Force-stop the app. Tap the launcher icon.
- [ ] Dashboard appears. The quote card region shows neutral dark grey (`#1E1E1E`) during the brief image decode window, avoiding black flashes.
- [ ] The quote image fades in smoothly via crossfade within 1–3 frames.
- [ ] Navigate away from Dashboard (e.g. to Settings) and return — card renders smoothly with no black flash.
- [ ] If a quote has a blank/null image URL, the card placeholder renders with the same neutral `#1E1E1E` surface.
