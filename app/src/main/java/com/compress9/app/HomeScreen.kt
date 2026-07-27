package com.compress9.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onVideoSelected: () -> Unit,
    onImageSelected: () -> Unit
) {
    var showAbout by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val gitHubUrl = "https://github.com/glitchsumon/compress9"
    val webUrl = "https://compress9.cu.ma"

    val prefs = context.getSharedPreferences("compress9_prefs", Context.MODE_PRIVATE)
    var showWelcome by remember { mutableStateOf(!prefs.getBoolean("welcome_dismissed", false)) }

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        val info = UpdateChecker(context).check()
        if (info.hasUpdate) {
            updateInfo = info
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compress9") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "What do you want to compress?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose an option below to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onVideoSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Compress Video", style = MaterialTheme.typography.titleMedium)
                    Text("MP4, MKV, AVI, MOV & more", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onImageSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Compress Image", style = MaterialTheme.typography.titleMedium)
                    Text("JPG, PNG, WEBP & more", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            TextButton(onClick = { showAbout = true }) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("About App", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            shape = RoundedCornerShape(20.dp),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Compress9", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Video & Image Compression Tool",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            text = {
                Column {
                    Text(
                        "Compress9 is a lightweight Android application designed to compress " +
                        "videos and images efficiently while preserving quality. Built with " +
                        "Kotlin and Jetpack Compose.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Developer",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Sumon",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Links",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(gitHubUrl)))
                        }) {
                            Icon(Icons.Default.Code, contentDescription = "GitHub",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp))
                        }
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
                        }) {
                            Icon(Icons.Default.Language, contentDescription = "Website",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text("Close")
                }
            }
        )
    }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            shape = RoundedCornerShape(20.dp),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Update Available", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("v${info.latestVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            },
            text = {
                Column {
                    Text(
                        "A new version of Compress9 is available!",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (info.releaseNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Release Notes:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(info.releaseNotes,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    updateInfo = null
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/glitchsumon/compress9/releases")))
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) { Text("Later") }
            }
        )
    }

    if (showWelcome) {
        AlertDialog(
            onDismissRequest = { showWelcome = false },
            shape = RoundedCornerShape(20.dp),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Welcome to Compress9", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Ads-Free & Open Source",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            },
            text = {
                Column {
                    Text(
                        "Compress9 is completely free, ad-free, and open source. " +
                        "Your privacy matters -- no data is collected.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("How to use:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("1. Select Video or Image from the home screen",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text("2. Choose a file from your device",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text("3. Adjust quality with the slider",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text("4. Tap Compress and wait for it to finish",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text("5. Find the output in Movies/Compress9 or Pictures/Compress9",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Supported formats: MP4, MKV, AVI, MOV, 3GP for video | " +
                            "JPG, PNG, WEBP for image",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        prefs.edit().putBoolean("welcome_dismissed", true).apply()
                        showWelcome = false
                    }) { Text("Don't Show Again") }
                    TextButton(onClick = {
                        showWelcome = false
                    }) { Text("Show Later") }
                }
            }
        )
    }
}
