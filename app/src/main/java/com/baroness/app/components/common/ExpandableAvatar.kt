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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
                Box(
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
                            .padding(top = statusBarHeight + 72.dp)
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
                                    modifier = Modifier.padding(10.dp),
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
                            .padding(20.dp)
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
