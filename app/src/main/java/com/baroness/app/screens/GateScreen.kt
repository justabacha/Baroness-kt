package com.baroness.app.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.baroness.app.R
import com.baroness.app.viewmodels.GateViewModel
import kotlinx.coroutines.delay

@Composable
fun GateScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: GateViewModel = viewModel(factory = GateViewModelFactory(context))
    val password by remember { viewModel.password }
    val isLoading by remember { viewModel.isLoading }
    val errorMessage by remember { viewModel.errorMessage }
    val isPasswordValid = viewModel.isPasswordValid

    var isPasswordVisible by remember { mutableStateOf(false) }

    var blinkAlpha by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            for (i in 1..8) {
                blinkAlpha = if (i % 2 == 1) 0f else 1f
                delay(300)
            }
            blinkAlpha = 1f
        } else {
            blinkAlpha = 1f
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.image_45),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.15f))
                .graphicsLayer {
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        renderEffect = BlurEffect(20f, 20f)
                    }
                }
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
                    .shadow(8.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
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

                    OutlinedTextField(
                        value = password,
                        onValueChange = { viewModel.updatePassword(it) },
                        placeholder = { Text("Enter secret key...", color = Color.LightGray) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(30.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isPasswordValid) Color(0xFF4caf50) else Color.White,
                            unfocusedBorderColor = Color.White,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White
                        ),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                    tint = Color.White
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
                                containerColor = if (isPasswordValid) Color(0xFF4d94ff).copy(alpha = 0.3f)
                                else Color.White.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isPasswordValid) Color(0xFF4d94ff) else Color(0xFF4caf50)
                            )
                        ) {
                            Text("Phesty", color = Color.White)
                        }

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
                                containerColor = if (isPasswordValid) Color(0xFFff4d6d).copy(alpha = 0.3f)
                                else Color.White.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isPasswordValid) Color(0xFFff4d6d) else Color(0xFF4caf50)
                            )
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

class GateViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return GateViewModel(context) as T
    }
}