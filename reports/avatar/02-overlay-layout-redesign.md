# Expandable Avatar — Overlay Layout Redesign

## Updated `ExpandableAvatar.kt`

Exact content of `app/src/main/java/com/baroness/app/components/common/ExpandableAvatar.kt`:

```kotlin
package com.baroness.app.components.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.baroness.app.ui.theme.LocalBaronessTheme
import com.baroness.app.ui.theme.LocalBaronessTypography
import kotlinx.coroutines.delay

@Composable
fun ExpandableAvatar(
    imageModel: Any?,
    contentDescription: String?,
    displayName: String,
    cardText: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    shape: Shape = CircleShape,
    scrimColor: Color = LocalBaronessTheme.current.bgStart.copy(alpha = 0.92f),
    onDismiss: (() -> Unit)? = null,
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(isClosing) {
        if (isClosing) {
            delay(220)
            isExpanded = false
            isClosing = false
            currentOnDismiss?.invoke()
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .border(borderWidth, borderColor, shape)
            .semantics {
                contentDescription?.let { this.contentDescription = it }
                role = Role.Button
            }
            .clickable(enabled = imageModel != null) {
                isExpanded = true
                isClosing = false
            }
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }

    if (isExpanded) {
        val dismiss = {
            if (!isClosing) isClosing = true
        }

        Dialog(
            onDismissRequest = dismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AnimatedVisibility(
                visible = !isClosing,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220)),
                exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.98f, animationSpec = tween(180))
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scrimColor)
                ) {
                    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    val request = ImageRequest.Builder(LocalContext.current)
                        .data(imageModel)
                        .size(Size.ORIGINAL)
                        .precision(Precision.EXACT)
                        .crossfade(true)
                        .build()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(top = statusBarHeight + maxHeight * 0.30f)
                    ) {
                        AsyncImage(
                            model = request,
                            contentDescription = contentDescription,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        if (displayName.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = displayName,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = LocalBaronessTypography.current.title,
                                color = Color.White
                            )
                        }
                        if (cardText.isNotEmpty()) {
                            if (displayName.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = cardText,
                                    modifier = Modifier.padding(16.dp),
                                    style = LocalBaronessTypography.current.body,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .statusBarsPadding()
                            .align(Alignment.TopEnd)
                            .padding(24.dp)
                            .size(28.dp)
                            .background(Color(0x4D464545), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                            .clip(CircleShape)
                            .semantics {
                                this.contentDescription = "Close"
                                this.role = Role.Button
                            }
                            .clickable(onClick = dismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "×",
                            color = Color.White,
                            fontSize = 18.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(bottom = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
```

## Call-site changes

### Dashboard (`screens/DashboardScreen.kt`)

Before:

```kotlin
ExpandableAvatar(
    imageModel = userProfile?.avatar,
    contentDescription = "Avatar",
    size = avatarSize,
    borderWidth = 1.5.dp,
    borderColor = Colors.purpleAccent,
    overlayContent = {
        Text(
            text = userProfile?.displayName ?: "User",
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        userProfile?.persona?.takeIf { it.isNotBlank() }?.let { persona ->
            Text(text = persona, color = Color.White.copy(alpha = 0.75f))
        }
    }
)
```

After:

```kotlin
ExpandableAvatar(
    imageModel = userProfile?.avatar,
    contentDescription = "Avatar",
    displayName = userProfile?.displayName ?: "You",
    cardText = "This is your profile picture",
    size = avatarSize,
    borderWidth = 1.5.dp,
    borderColor = Colors.purpleAccent
)
```

### Chat list (`components/ChatEntry.kt`)

Before:

```kotlin
ExpandableAvatar(
    imageModel = conversation.avatarUrl,
    contentDescription = conversation.displayName,
    modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape),
    size = 48.dp,
    borderWidth = 1.5.dp,
    borderColor = Colors.accent,
    overlayContent = {
        PhestyText(
            text = conversation.displayName,
            style = chatTypography.title,
            color = Color.White,
            fontFamily = chatTypography.title.fontFamily,
            textAlign = TextAlign.Center
        )
    }
)
```

After:

```kotlin
ExpandableAvatar(
    imageModel = conversation.avatarUrl,
    contentDescription = conversation.displayName,
    displayName = conversation.displayName,
    cardText = "This is ${conversation.displayName}'s profile picture",
    modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape),
    size = 48.dp,
    borderWidth = 1.5.dp,
    borderColor = Colors.accent
)
```

### Chat room header (`screens/ChatRoomScreen.kt`)

Before:

```kotlin
ExpandableAvatar(
    imageModel = participant?.avatarUrl
        ?: if (participant?.id == "friday") "https://img.icons8.com/fluency/48/artificial-intelligence.png" else null,
    contentDescription = participant?.displayName ?: "Avatar",
    modifier = Modifier
        .size(38.dp)
        .border(1.5.dp, Color.Black.copy(alpha = 0.75f), CircleShape)
        .padding(0.5.dp),
    size = 38.dp,
    borderWidth = 1.dp,
    borderColor = Color.White.copy(alpha = 0.9f),
    overlayContent = {
        Text(
            text = if (participant?.id == "friday") "Friday" else participant?.displayName ?: "Participant",
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        participant?.id?.takeIf { it != "friday" }?.let { personaId ->
            Text(text = personaId, color = Color.White.copy(alpha = 0.75f))
        }
    }
)
```

After:

```kotlin
val avatarDisplayName = if (participant?.id == "friday") {
    "Friday"
} else {
    participant?.displayName ?: "Participant"
}
ExpandableAvatar(
    imageModel = participant?.avatarUrl
        ?: if (participant?.id == "friday") "https://img.icons8.com/fluency/48/artificial-intelligence.png" else null,
    contentDescription = participant?.displayName ?: "Avatar",
    displayName = avatarDisplayName,
    cardText = "This is $avatarDisplayName's profile picture",
    modifier = Modifier
        .size(38.dp)
        .border(1.5.dp, Color.Black.copy(alpha = 0.75f), CircleShape)
        .padding(0.5.dp),
    size = 38.dp,
    borderWidth = 1.dp,
    borderColor = Color.White.copy(alpha = 0.9f)
)
```

### Message bubble (`components/chat/MessageBubble.kt`)

Before:

```kotlin
ExpandableAvatar(
    imageModel = imageModel,
    contentDescription = participant?.displayName ?: "Avatar",
    modifier = modifier.background(Color.White.copy(alpha = 0.1f), CircleShape),
    size = 36.dp,
    borderWidth = borderWidth,
    borderColor = borderColor,
    overlayContent = {
        Text(
            text = participant?.displayName ?: if (isFriday) "Friday" else "Participant",
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
)
```

After:

```kotlin
val displayName = participant?.displayName ?: if (isFriday) "Friday" else "Participant"
ExpandableAvatar(
    imageModel = imageModel,
    contentDescription = participant?.displayName ?: "Avatar",
    displayName = displayName,
    cardText = "This is $displayName's profile picture",
    modifier = modifier.background(Color.White.copy(alpha = 0.1f), CircleShape),
    size = 36.dp,
    borderWidth = borderWidth,
    borderColor = borderColor
)
```

### Wishlist header — Phesty (`components/wishlist/WishlistHeader.kt`)

Before:

```kotlin
ExpandableAvatar(
    imageModel = avatarP ?: "https://img.icons8.com/color/96/test-account.png",
    contentDescription = "Phesty",
    modifier = Modifier.background(Color(0xFF2A2A2A), CircleShape),
    size = 44.dp,
    borderWidth = 2.dp,
    borderColor = Color(0xFF34C759),
    overlayContent = {
        Text(text = "Phesty", color = Color.White, fontWeight = FontWeight.Bold)
    }
)
```

After:

```kotlin
ExpandableAvatar(
    imageModel = avatarP ?: "https://img.icons8.com/color/96/test-account.png",
    contentDescription = "Phesty",
    displayName = "Phesty",
    cardText = "This is Phesty's profile picture",
    modifier = Modifier.background(Color(0xFF2A2A2A), CircleShape),
    size = 44.dp,
    borderWidth = 2.dp,
    borderColor = Color(0xFF34C759)
)
```

### Wishlist header — Baroness (`components/wishlist/WishlistHeader.kt`)

Before:

```kotlin
ExpandableAvatar(
    imageModel = avatarB ?: "https://img.icons8.com/color/96/test-account.png",
    contentDescription = "Baroness",
    modifier = Modifier
        .offset(x = (-10).dp)
        .zIndex(1f)
        .background(Color(0xFF2A2A2A), CircleShape),
    size = 44.dp,
    borderWidth = 2.dp,
    borderColor = Color(0xFF4285F4),
    overlayContent = {
        Text(text = "Baroness", color = Color.White, fontWeight = FontWeight.Bold)
    }
)
```

After:

```kotlin
ExpandableAvatar(
    imageModel = avatarB ?: "https://img.icons8.com/color/96/test-account.png",
    contentDescription = "Baroness",
    displayName = "Baroness",
    cardText = "This is Baroness's profile picture",
    modifier = Modifier
        .offset(x = (-10).dp)
        .zIndex(1f)
        .background(Color(0xFF2A2A2A), CircleShape),
    size = 44.dp,
    borderWidth = 2.dp,
    borderColor = Color(0xFF4285F4)
)
```

### Settings Center (`screens/settings/SettingsCenterScreen.kt`)

Before:

```kotlin
ExpandableAvatar(
    imageModel = userProfile?.avatar,
    contentDescription = "Avatar",
    modifier = Modifier.matchParentSize(),
    size = 54.dp,
    borderWidth = 1.5.dp,
    borderColor = Color.White.copy(alpha = 0.3f),
    overlayContent = {
        Text(
            text = userProfile?.displayName ?: "Phesty",
            color = Color.White,
            fontFamily = AppFonts.PlayfairDisplay,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        userProfile?.persona?.takeIf { it.isNotBlank() }?.let { persona ->
            Text(text = persona, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
        }
    }
)
```

After:

```kotlin
ExpandableAvatar(
    imageModel = userProfile?.avatar,
    contentDescription = "Avatar",
    displayName = userProfile?.displayName ?: "You",
    cardText = "This is your profile picture",
    modifier = Modifier.matchParentSize(),
    size = 54.dp,
    borderWidth = 1.5.dp,
    borderColor = Color.White.copy(alpha = 0.3f)
)
```

## Typography and color roles

- Display name: `LocalBaronessTypography.current.title`, colored `Color.White`.
- Card surface: M3 `MaterialTheme.colorScheme.surfaceVariant`, rounded to 16.dp.
- Card text: `LocalBaronessTypography.current.body`, colored with the matching M3 `MaterialTheme.colorScheme.onSurfaceVariant`.
- The card is omitted when `cardText` is empty. An empty display name omits its own line and spacing.

## Preserved behavior

The Coil `ImageRequest` is unchanged: it still uses `Size.ORIGINAL`, `Precision.EXACT`, and crossfade. Dialog properties, dismiss flow, `AnimatedVisibility`, fade/scale transitions and timings are unchanged. The collapsed avatar and scrim default are unchanged. The close button retains its 28.dp appearance and behavior, with `statusBarsPadding()` added to its outer box.

## Build and tests

- `:app:testDebugUnitTest` — **failed in two existing live endpoint probes**: `testLiveFridayEndpointProbe_UnauthenticatedReturns401` and `testLiveFridayEndpointProbe_WithValidTokenReturns200` failed with `UnknownHostException` while trying to reach the configured Supabase endpoint. The other 24 tests completed without failure.
- `:app:assembleDebug` — **passed**, including a standalone assembly.
- APK path: `app/build/outputs/apk/debug/baroness-v1.0.1.apk` (47,119,011 bytes).
- No device or adb commands were run.

## Manual verification checklist for the operator

- [ ] Open each of the seven sites and tap the avatar. Verify the overlay shows a square, full-width image with 2.dp side gaps and 12.dp corners, a left-aligned name, and an M3 card with the placeholder text.
- [ ] Verify the image begins approximately 30% below the status bar.
- [ ] Verify the image is sharp, with no blur or downsampling artifacts.
- [ ] Verify the top-right X does not overlap the status bar.
- [ ] Verify system Back closes the overlay.
- [ ] With large accessibility text scaling, verify the card remains reachable by scrolling when necessary.
- [ ] Verify Dashboard and Settings say “This is your profile picture”; other screens use the persona's name.

## Post-Review Fix

### Vertical position

Before:

```kotlin
BoxWithConstraints(
    modifier = Modifier
        .fillMaxSize()
        .background(scrimColor)
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = statusBarHeight + maxHeight * 0.30f)
    ) {
```

After:

```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .background(scrimColor)
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = statusBarHeight + 72.dp)
    ) {
```

`BoxWithConstraints` was removed because its `maxHeight` was only used by the removed percentage offset. The scroll container remains. The close button bottom is at approximately status-bar inset + 24.dp + 28.dp = inset + 52.dp; the image starts at inset + 72.dp, leaving the intended 20.dp gap. Since both offsets include the same measured status-bar inset, a tall status bar does not reduce that gap or cause overlap.

### ProfileSettingsPage container pattern

ProfileSettingsPage uses two related patterns:

1. The profile/avatar and identity cards use M3 `Card`, not `Surface` or a manually composed `Box`. Their shape is `RoundedCornerShape(20.dp)`, background is `Color.White.copy(alpha = 0.05f)`, and `cardBorder` is `BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))`. The content `Column` uses `.fillMaxWidth().padding(20.dp)`.
2. The read-only settings rows use this exact modifier chain (there is no border on this row pattern):

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .clip(RoundedCornerShape(12.dp))
    .background(Color.White.copy(alpha = 0.08f))
    .padding(horizontal = 14.dp, vertical = 12.dp)
```

The expandable overlay's information panel follows the bordered settings **Card** pattern, rather than the unbordered row: 20.dp rounded M3 `Card`, white 5%-alpha container, 1.dp theme-primary border at 50% alpha, and 20.dp content padding. Exact ProfileSettingsPage source:

```kotlin
val accentColor = MaterialTheme.colorScheme.primary
val cardBorder = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))

Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
        containerColor = Color.White.copy(alpha = 0.05f)
    ),
    border = cardBorder
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Card content
    }
}
```

Overlay card before:

```kotlin
Surface(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant
) {
    Text(
        text = cardText,
        modifier = Modifier.padding(16.dp),
        style = LocalBaronessTypography.current.body,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
```

Overlay card after:

```kotlin
Card(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
        containerColor = Color.White.copy(alpha = 0.05f)
    ),
    border = BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    )
) {
    Text(
        text = cardText,
        modifier = Modifier.padding(20.dp),
        style = LocalBaronessTypography.current.body,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
```

### Preserved behavior and verification

The Coil request (`Size.ORIGINAL`, `Precision.EXACT`, crossfade), Dialog properties, animations/timings, dismiss flow, and all call sites remain unchanged. The collapsed avatar is unchanged. Card text, typography, and text color are unchanged.

- `:app:assembleDebug` — **passed**.
- APK: `app/build/outputs/apk/debug/baroness-v1.0.1.apk`.
- `:app:testDebugUnitTest` was not rerun for this layout-only adjustment. In the previous phase, two existing live Supabase endpoint probes failed with `UnknownHostException`; the remaining 24 tests passed.

Manual verification:

- [ ] Open the overlay from each of the seven sites.
- [ ] Verify the image sits near the top, just below the X close button—not 30% down the screen.
- [ ] Verify the card has a visible border matching the ProfileSettingsPage card.
- [ ] Verify card background, corner radius, and border match the settings page.
- [ ] Verify there is no regression in the collapsed avatar.
