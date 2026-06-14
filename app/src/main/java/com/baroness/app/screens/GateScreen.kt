package com.baroness.app.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.navigation.NavController
import com.baroness.app.viewmodels.GateViewModel

@Composable
fun GateScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: GateViewModel = viewModel(factory = GateViewModelFactory(context))
    val password by remember { viewModel.password }
    val isLoading by remember { viewModel.isLoading }
    val errorMessage by remember { viewModel.errorMessage }
    val isPasswordValid = viewModel.isPasswordValid

    var isPasswordVisible by remember { mutableStateOf(false) }

    // Blinking animation for error
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1a1a2e), Color(0xFF16213e))
                )
            )
    ) {
        // Blur overlay effect
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "BARONESS GATE CHECK",
                        color = Color.White,
                        fontSize = 20.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Baroness prioritize your security.",
                        color = Color(0xFF00f8db),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    // Password input with eye toggle
                    OutlinedTextField(
                        value = password,
                        onValueChange = { viewModel.updatePassword(it) },
                        placeholder = { Text("Enter secret key...", color = Color.LightGray) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isPasswordValid) Color(0xFF4caf50) else Color.White,
                            unfocusedBorderColor = Color.White,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White
                        ),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Text(
                                    text = if (isPasswordVisible) "🙈" else "👁️",
                                    fontSize = 20.sp
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Phesty Button
                        Button(
                            onClick = {
                                viewModel.onGateSelected("phesty") { profileId, personaId ->
                                    if (profileId != null) {
                                        navController.navigate("dashboard") {
                                            popUpTo("gate") { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate("profile_setup/$personaId")
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPasswordValid) Color(0xFF4d94ff).copy(alpha = 0.6f)
                                else Color.White.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Phesty", color = Color.White)
                        }

                        // Baroness Button
                        Button(
                            onClick = {
                                viewModel.onGateSelected("baroness") { profileId, personaId ->
                                    if (profileId != null) {
                                        navController.navigate("dashboard") {
                                            popUpTo("gate") { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate("profile_setup/$personaId")
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPasswordValid) Color(0xFFff4d6d).copy(alpha = 0.6f)
                                else Color.White.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Baroness", color = Color.White)
                        }
                    }

                    if (isLoading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Color.White)
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "ⓘ ${errorMessage!!}",
                            color = Color(0xFFff4d6d),
                            fontSize = 14.sp,
                            modifier = Modifier.alpha(blinkAlpha)
                        )
                    }
                }
            }
        }
    }
}

// Factory for ViewModel with context
class GateViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return GateViewModel(context) as T
    }
}