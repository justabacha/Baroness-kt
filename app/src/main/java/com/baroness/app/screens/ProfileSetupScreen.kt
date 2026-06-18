package com.baroness.app.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.baroness.app.R
import com.baroness.app.viewmodels.ProfileSetupViewModel

@Composable
fun ProfileSetupScreen(
    navController: NavController,
    currentPersonaId: String
) {
    val context = LocalContext.current
    val viewModel: ProfileSetupViewModel = viewModel(
        factory = ProfileSetupViewModelFactory(context, currentPersonaId)
    )

    val name by remember { viewModel.name }
    val avatarUri by remember { viewModel.avatarUri }
    val isLoading by remember { viewModel.isLoading }
    val isLoadingProfile by remember { viewModel.isLoadingProfile }
    val errorMessage by remember { viewModel.errorMessage }

    var showLogoutDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setSelectedImage(it) }
    }

    if (isLoadingProfile) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFff4d6d))
        }
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
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
                    .background(Color.Black.copy(alpha = 0.2f))
                    .graphicsLayer {
                        if (android.os.Build.VERSION.SDK_INT >= 31) {
                            renderEffect = BlurEffect(8f, 8f)
                        }
                    }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 32.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.1f)),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Vibe Profile 🌸",
                            color = Color(0xFFff4d6d),
                            fontSize = 22.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Set your identity for the vault.",
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color(0xFFff4d6d), CircleShape)
                                .clickable { imagePickerLauncher.launch("image/*") }
                                .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarUri != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(avatarUri),
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = android.R.drawable.ic_menu_camera),
                                    contentDescription = "Add photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedTextField(
                            value = name,
                            onValueChange = { viewModel.updateName(it) },
                            placeholder = { Text("Enter your nickname...", color = Color.LightGray) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFff4d6d),
                                unfocusedBorderColor = Color.White,
                                focusedLabelColor = Color.White,
                                unfocusedLabelColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFff4d6d),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Button(
                            onClick = {
                                viewModel.onSave { success, _ ->
                                    if (success) {
                                      val previousRoute = navController.previousBackStackEntry?.destination?.route
                                        if (previousRoute == "dashboard") {
                                           navController.popBackStack()
                                        } else {
                                            navController.navigate("dashboard") {
                                                popUpTo(0) { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(25.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                            } else {
                                Text("Vibe In ✨", color = Color.Black, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                        }
                    }
                }

                Button(
                    onClick = { showLogoutDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFFff4d6d)
                    ),
                    shape = RoundedCornerShape(40.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .height(48.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(40.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color.Gray.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(40.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Logout",
                        tint = Color(0xFFff4d6d)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logout", color = Color(0xFFff4d6d))
                }
            }
        }
    }

    if (showLogoutDialog) {
        Dialog(
            onDismissRequest = { showLogoutDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.8f)),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Logout?", color = Color(0xFFff4d6d), fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Are you sure you want to go back to the Gate?", color = Color.White, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showLogoutDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(30.dp)
                        ) {
                            Text("Cancel", color = Color.White)
                        }
                        Button(
                            onClick = {
                                viewModel.onLogout {
                                    navController.navigate("gate") {
                                        popUpTo(0) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFff4d6d)),
                            shape = RoundedCornerShape(30.dp)
                        ) {
                            Text("Logout", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

class ProfileSetupViewModelFactory(
    private val context: Context,
    private val currentPersonaId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return ProfileSetupViewModel(context, currentPersonaId) as T
    }
}