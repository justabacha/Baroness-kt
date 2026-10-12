# Expandable Avatar — Implementation Report

## Offline avatar verification

The repository confirms the offline source paths, but device-level offline cold-start behavior cannot be exercised in this environment. No device or adb commands were run.

| Site | Model and first-view path | Offline cold-start assessment after one successful online view |
|---|---|---|
| Dashboard | `DashboardViewModel` calls `AvatarRepository.getCachedAvatar(profile.avatar)` and replaces the model with the returned absolute `filesDir/user_avatars/avatar_<timestamp>.jpg` path. | The repository reuses a matching, existing local path without networking. Passes the source-level check. |
| Settings Center | `SettingsCenterScreen` calls `AvatarRepository.getCachedAvatar(profile.avatar)` and stores the resulting local path in `userProfile.avatar`. | Same matching-file reuse as Dashboard. Passes the source-level check. |
| ChatEntry | Raw `conversation.avatarUrl` (remote URL); no local repository lookup. | The shared Coil 2.7 loader has a disk cache under `filesDir/image_cache` and sets `respectCacheHeaders(false)`. A previously loaded URL is expected to be served from disk. Source-level only. |
| ChatRoomScreen header | Raw `participant.avatarUrl`; Friday uses the existing Icons8 fallback URL if there is no participant URL. | Same persistent Coil disk-cache path. The Friday fallback follows that same behavior. Source-level only. |
| MessageBubble | Raw `participant.avatarUrl`, or the existing Friday / generic fallback URL. | Same persistent Coil disk-cache path. Source-level only. |
| WishlistHeader (both avatars) | Raw `avatarP` / `avatarB`, or the existing `test-account.png` fallback URL. | Same persistent Coil disk-cache path for the URL seen online, including fallback URLs. Source-level only. |

`AvatarRepository` catches download failures and returns `null`; the two repository-backed screens then retain their original URL as fallback. Thus local-file reuse is confirmed for a successful first online load; actual airplane-mode rendering should still be checked by the operator checklist below. No URL site was rerouted because source inspection found the existing persistent Coil disk cache expected to cover previously viewed URLs.

## Implementation

### `ExpandableAvatar.kt`

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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.delay

@Composable
fun ExpandableAvatar(
    imageModel: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    shape: Shape = CircleShape,
    scrimColor: Color = LocalBaronessTheme.current.bgStart.copy(alpha = 0.92f),
    overlayContent: (@Composable ColumnScope.() -> Unit)? = null,
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
                    val imageSize = minOf(maxWidth, maxHeight) * 0.78f
                    val request = ImageRequest.Builder(LocalContext.current)
                        .data(imageModel)
                        .size(Size.ORIGINAL)
                        .precision(Precision.EXACT)
                        .crossfade(true)
                        .build()

                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AsyncImage(
                            model = request,
                            contentDescription = contentDescription,
                            modifier = Modifier
                                .size(imageSize)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                        if (overlayContent != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                overlayContent()
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
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

### Call-site changes

The snippets below show each replaced avatar block and its new implementation; surrounding screen and row behavior is unchanged.

#### Dashboard — `screens/DashboardScreen.kt`

Before:

```kotlin
Image(
    painter = rememberAsyncImagePainter(userProfile?.avatar),
    contentDescription = "Avatar",
    modifier = Modifier
        .size(avatarSize)
        .clip(CircleShape)
        .border(1.5.dp, Colors.purpleAccent, CircleShape),
    contentScale = ContentScale.Crop
)
```

After:

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

#### Chat list — `components/ChatEntry.kt`

Before:

```kotlin
Box(
    modifier = Modifier
        .size(48.dp)
        .clip(CircleShape)
        .background(Color.White.copy(alpha = 0.1f))
        .border(1.5.dp, Colors.accent, CircleShape),
    contentAlignment = Alignment.Center
) {
    if (!conversation.avatarUrl.isNullOrEmpty()) {
        AsyncImage(
            model = conversation.avatarUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        val initial = conversation.displayName.take(1).uppercase()
        PhestyText(
            text = initial,
            style = chatTypography.title,
            color = Color.White,
            fontFamily = chatTypography.title.fontFamily,
            textAlign = TextAlign.Center
        )
    }
}
```

After:

```kotlin
if (!conversation.avatarUrl.isNullOrEmpty()) {
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
} else {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .border(1.5.dp, Colors.accent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val initial = conversation.displayName.take(1).uppercase()
        PhestyText(
            text = initial,
            style = chatTypography.title,
            color = Color.White,
            fontFamily = chatTypography.title.fontFamily,
            textAlign = TextAlign.Center
        )
    }
}
```

The row-level navigation click is retained. The nested avatar clickable consumes avatar taps; the initial-letter fallback remains unchanged and is not routed through the overlay.

#### Chat room header — `screens/ChatRoomScreen.kt`

Before:

```kotlin
Box(
    modifier = Modifier
        .size(38.dp)
        .border(1.5.dp, Color.Black.copy(alpha = 0.75f), CircleShape)
        .padding(0.5.dp)
        .clip(CircleShape)
        .border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape)
) {
    if (participant?.avatarUrl != null) {
        AsyncImage(
            model = participant.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else if (participant?.id == "friday") {
        AsyncImage(
            model = "https://img.icons8.com/fluency/48/artificial-intelligence.png",
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}
```

After:

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

The participant avatar URL and Friday fallback are retained. The outer black and inner white borders are passed through the modifier and border parameters.

#### Message bubble — `components/chat/MessageBubble.kt` (`AvatarIsland`)

Before:

```kotlin
Box(
    modifier = modifier
        .size(36.dp)
        .border(borderWidth, borderColor, CircleShape)
        .clip(CircleShape)
        .background(Color.White.copy(alpha = 0.1f))
) {
    if (participant?.avatarUrl != null) {
        AsyncImage(
            model = participant.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        val fallbackUrl = if (isFriday) "https://img.icons8.com/fluency/48/artificial-intelligence.png" else "https://img.icons8.com/fluency/48/user-male-circle.png"
        AsyncImage(
            model = fallbackUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}
```

After:

```kotlin
val imageModel = participant?.avatarUrl ?: if (isFriday) {
    "https://img.icons8.com/fluency/48/artificial-intelligence.png"
} else {
    "https://img.icons8.com/fluency/48/user-male-circle.png"
}
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

The existing bubble click/long-click modifier remains on the bubble content only; `AvatarIsland` is a separate sibling click target.

#### Wishlist header — `components/wishlist/WishlistHeader.kt`

Before:

```kotlin
Box(
    modifier = Modifier
        .size(44.dp)
        .border(2.dp, Color(0xFF34C759), CircleShape)
        .clip(CircleShape)
        .background(Color(0xFF2A2A2A)),
    contentAlignment = Alignment.Center
) {
    AsyncImage(
        model = avatarP ?: "https://img.icons8.com/color/96/test-account.png",
        contentDescription = "Phesty",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}

Box(
    modifier = Modifier
        .size(44.dp)
        .offset(x = (-10).dp)
        .zIndex(1f)
        .border(2.dp, Color(0xFF4285F4), CircleShape)
        .clip(CircleShape)
        .background(Color(0xFF2A2A2A)),
    contentAlignment = Alignment.Center
) {
    AsyncImage(
        model = avatarB ?: "https://img.icons8.com/color/96/test-account.png",
        contentDescription = "Baroness",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}
```

After:

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

The fallback URLs, size, background, and green/blue borders are preserved.

#### Settings Center — `screens/settings/SettingsCenterScreen.kt`

Before:

```kotlin
if (userProfile?.avatar != null) {
    Image(
        painter = rememberAsyncImagePainter(userProfile.avatar),
        contentDescription = "Avatar",
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape),
        contentScale = ContentScale.Crop
    )
} else {
    Surface(
        modifier = Modifier.size(54.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.3f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = (userProfile?.displayName?.take(1) ?: "P").uppercase(),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
```

After:

```kotlin
Box(modifier = Modifier.size(54.dp)) {
    if (userProfile?.avatar == null) {
        Surface(
            modifier = Modifier.matchParentSize(),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = (userProfile?.displayName?.take(1) ?: "P").uppercase(),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
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
}
```

The existing initial-letter placeholder remains visible when the avatar model is null; the shared composable correctly disables expansion for a null model.

### uCrop

`app/src/main/java/com/baroness/app/utils/AvatarCrop.kt` changes only the maximum output resolution:

```diff
-.withMaxResultSize(1024, 1024)
+.withMaxResultSize(2048, 2048)
```

## Adjustments and scope

- ChatEntry keeps the initial-letter fallback exactly as a non-expandable fallback; only an actual avatar URL gets the overlay. The parent row navigation click remains in place.
- Chat room header uses the existing Friday fallback URL in the overlay model, and displays only “Friday” for that persona; other participants show their display name and ID.
- MessageBubble keeps existing Friday and generic fallback image URLs. The avatar click modifier is scoped to the avatar, not its surrounding message bubble.
- Settings Center preserves its no-image initial placeholder while making real cached/local avatars expandable.
- Dashboard overlay metadata is limited to display name and persona. Chat and wishlist overlays contain only the requested names/persona labels.
- Out-of-scope Profile Setup, Profile Settings, and in-app notification files were not changed. Chat message send/receive logic, sync/RLS/database code, `MainApplication` loader, `AvatarRepository`, and theme files were not changed.

## Build and test

- `:app:testDebugUnitTest` — **passed**.
- `:app:assembleDebug` — **passed**.
- APK: `app/build/outputs/apk/debug/baroness-v1.0.1.apk` (47,380,108 bytes).
- No Compose UI test was added; validating this modal interaction requires a device/UI test run, which is outside the allowed device/adb scope.

## Manual verification checklist for the operator

- Dashboard: tap avatar → overlay opens with image at full resolution, display name and persona shown. X closes. Back closes.
- Chat list: tap the avatar → overlay opens. Tap the row outside the avatar → navigates into the room as before.
- Chat room header: tap avatar → overlay opens.
- Message bubble: tap avatar → overlay opens. Tap the bubble → no overlay.
- Wishlist header: tap Phesty avatar → overlay with “Phesty”. Tap Baroness → overlay with “Baroness”.
- Settings center: tap avatar → overlay opens.
- Close overlay via X. Close via back. Both work.
- Zoom check: with a 1024×1024 avatar, the overlay image is sharp (no visible blur). If blurry, report.
- Offline: after loading avatars once online, airplane mode, cold start, open each screen, tap avatar. Image still renders sharply.
- Profile Setup and Profile Settings: tap avatar → still opens the picker, NOT the overlay.
