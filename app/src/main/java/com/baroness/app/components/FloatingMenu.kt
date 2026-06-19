package com.baroness.app.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.baroness.app.R
import com.baroness.app.models.MenuItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MENU_SIZE = 60
private const val EDGE_OFFSET = 10
private const val PANEL_GAP = 4
private const val PANEL_WIDTH = 62
private const val SCREEN_SAFE_MARGIN = 20
private const val DRAG_THRESHOLD = 6f

private val Y_KEY = floatPreferencesKey("floating_menu_y")
private val X_KEY = floatPreferencesKey("floating_menu_x")
private val SIDE_KEY = stringPreferencesKey("floating_menu_side")

val Context.menuStore: DataStore<Preferences> by preferencesDataStore("floating_menu")

val menuItems = listOf(
    MenuItem("MESSAGES", "https://img.icons8.com/color/48/imessage.png", "Messages", "#00ff4c"),
    MenuItem("J.A.R.V.I.S", "https://img.icons8.com/fluency/48/artificial-intelligence.png", "Friday", "#aa00ff"),
    MenuItem("PHOTOS", "https://img.icons8.com/color/48/google-photos-new.png", "Photos", "#ff5050"),
    MenuItem("WISHLIST", "https://img.icons8.com/fluency/48/star.png", "Wishlist", "#ffc800")
)

@Composable
fun FloatingMenu(
    navController: NavController,
    initialSide: String = "right",
    onPanelVisibilityChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current

    val screenWidthPx = windowInfo.containerSize.width.toFloat()
    val screenHeightPx = windowInfo.containerSize.height.toFloat()

    var side by remember { mutableStateOf(initialSide) }
    var panelVisible by remember { mutableStateOf(false) }
    var savedY by remember { mutableFloatStateOf(screenHeightPx / 2 - MENU_SIZE / 2) }
    var savedX by remember { mutableFloatStateOf(0f) }
    val offsetY = remember { Animatable(savedY) }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }


    var panelHeightPx by remember { mutableFloatStateOf(0f) }

    var panelAbove by remember { mutableStateOf(false) }

    val menuPx = with(density) { MENU_SIZE.dp.toPx() }
    val edgePx = with(density) { EDGE_OFFSET.dp.toPx() }
    val safeMarginPx = with(density) { SCREEN_SAFE_MARGIN.dp.toPx() }
    val gapPx = with(density) { PANEL_GAP.dp.toPx() }

    fun restingX(s: String): Float =
        if (s == "left") edgePx else screenWidthPx - menuPx - edgePx

    LaunchedEffect(Unit) {
        val prefs = context.menuStore.data.first()
        prefs[Y_KEY]?.let {
            val clamped = it.coerceIn(safeMarginPx, screenHeightPx - MENU_SIZE - safeMarginPx)
            savedY = clamped
            offsetY.snapTo(clamped)
        }
        prefs[X_KEY]?.let {
            val clamped = it.coerceIn(edgePx, screenWidthPx - menuPx - edgePx)
            savedX = clamped
            offsetX.snapTo(clamped)
        } ?: run {
            val defaultX = restingX(initialSide)
            savedX = defaultX
            offsetX.snapTo(defaultX)
        }
        prefs[SIDE_KEY]?.let { side = it }
        loaded = true
    }

    suspend fun persist() {
        context.menuStore.edit {
            it[Y_KEY] = savedY
            it[X_KEY] = savedX
            it[SIDE_KEY] = side
        }
    }

    fun closePanel() {
        panelVisible = false
        onPanelVisibilityChange(false)
    }

    fun computePanelAbove(iconY: Float, panelH: Float): Boolean {
        if (panelH <= 0f) return false
        val spaceBelow = screenHeightPx - safeMarginPx - (iconY + menuPx + gapPx)

        return spaceBelow < panelH && (iconY - gapPx - panelH) >= safeMarginPx
    }

    fun openPanel() {
        val currentY = offsetY.value
        savedY = currentY
        savedX = offsetX.value

        panelAbove = computePanelAbove(currentY, panelHeightPx)

        panelVisible = true
        onPanelVisibilityChange(true)
    }

    LaunchedEffect(panelHeightPx) {
        if (panelVisible && panelHeightPx > 0f) {
            val shouldFlip = computePanelAbove(offsetY.value, panelHeightPx)
            if (shouldFlip != panelAbove) {
                panelAbove = shouldFlip
            }
        }
    }

    Box(Modifier.fillMaxSize()) {

        AnimatedVisibility(
            visible = panelVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { closePanel() })
                    }
            )
        }

        AnimatedVisibility(
            visible = panelVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val iconCenterPx = savedX + menuPx / 2f
            val panelWPx = with(density) { PANEL_WIDTH.dp.toPx() }
            val panelXPx = (iconCenterPx - panelWPx / 2f).coerceIn(
                edgePx,
                screenWidthPx - panelWPx - edgePx
            )

            val panelTopPx = if (panelAbove) {
                (offsetY.value - gapPx - panelHeightPx).coerceIn(
                    safeMarginPx,
                    offsetY.value - gapPx
                )
            } else {
                offsetY.value + menuPx + gapPx
            }

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(panelXPx.roundToInt(), panelTopPx.roundToInt())
                    }
                    .width(PANEL_WIDTH.dp)
                    .onGloballyPositioned { coords ->
                        val h = coords.size.height.toFloat()
                        if (h > 0f && h != panelHeightPx) {
                            panelHeightPx = h
                        }
                    }
                    .shadow(
                        elevation = 30.dp,
                        shape = RoundedCornerShape(32.dp),
                        ambientColor = Color.Black.copy(alpha = 0.5f),
                        spotColor = Color.Black.copy(alpha = 0.7f)
                    )
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.16f),
                                Color.White.copy(alpha = 0.06f),
                                Color.Black.copy(alpha = 0.03f)
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.45f),
                                Color.White.copy(alpha = 0.12f)
                            )
                        ),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    menuItems.forEach { item ->
                        val glow = try {
                            Color(item.glowColor.toColorInt())
                        } catch (_: Exception) {
                            Color.Magenta
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    closePanel()
                                    navController.navigate(item.route)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .background(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                glow.copy(alpha = 0.7f),
                                                glow.copy(alpha = 0.25f),
                                                Color.Transparent
                                            ),
                                            center = Offset.Unspecified,
                                            radius = with(density) { 28.dp.toPx() }
                                        ),
                                        shape = CircleShape
                                    )
                            )
                            AsyncImage(
                                model = item.iconUrl,
                                contentDescription = item.label,
                                modifier = Modifier.size(34.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }

        // ---- Menu icon ----
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt())
                }
                .size(MENU_SIZE.dp)
                .shadow(
                    elevation = 25.dp,
                    shape = CircleShape,
                    clip = false
                )
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                .pointerInput(loaded) {
                    if (!loaded) return@pointerInput
                    var totalDx = 0f
                    var totalDy = 0f
                    var didDrag = false
                    detectDragGestures(
                        onDragStart = {
                            totalDx = 0f
                            totalDy = 0f
                            didDrag = false
                        },
                        onDragEnd = {
                            if (didDrag && !panelVisible) {
                                val centerXPx = offsetX.value + menuPx / 2f
                                side = if (centerXPx < screenWidthPx / 2) "left" else "right"
                                val finalY = offsetY.value.coerceIn(
                                    safeMarginPx,
                                    screenHeightPx - MENU_SIZE - safeMarginPx
                                )
                                val finalX =
                                    if (side == "left") edgePx else screenWidthPx - menuPx - edgePx
                                savedY = finalY
                                savedX = finalX
                                scope.launch {
                                    offsetY.animateTo(finalY, spring())
                                    offsetX.animateTo(finalX, spring())
                                    persist()
                                }
                            }
                        },
                        onDragCancel = { },
                        onDrag = { change, drag ->
                            change.consume()
                            totalDx += drag.x
                            totalDy += drag.y
                            if (!didDrag && (kotlin.math.abs(totalDx) > DRAG_THRESHOLD ||
                                        kotlin.math.abs(totalDy) > DRAG_THRESHOLD)
                            ) {
                                didDrag = true
                            }
                            if (didDrag && !panelVisible) {
                                val newX = (offsetX.value + drag.x).coerceIn(
                                    0f,
                                    screenWidthPx - menuPx
                                )
                                val newY = (offsetY.value + drag.y).coerceIn(
                                    safeMarginPx,
                                    screenHeightPx - MENU_SIZE - safeMarginPx
                                )
                                scope.launch {
                                    offsetX.snapTo(newX)
                                    offsetY.snapTo(newY)
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        if (panelVisible) closePanel() else openPanel()
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.icon),
                contentDescription = "Menu",
                modifier = Modifier
                    .size((MENU_SIZE - 10).dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    }
}