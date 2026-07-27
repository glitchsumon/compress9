package com.compress9.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCompressScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedImage by remember { mutableStateOf<ImageFile?>(null) }
    var quality by remember { mutableIntStateOf(70) }
    var isCompressing by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<ImageCompressor.CompressionResult?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val cursor = context.contentResolver.query(it, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        val name = if (nameIdx >= 0) c.getString(nameIdx) else "Unknown"
                        val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                        selectedImage = ImageFile(it, name, size)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Image Compressor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedImage == null) {
                OutlinedButton(
                    onClick = { picker.launch("image/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Image File")
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(selectedImage!!.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Size: ${formatSize(selectedImage!!.size)}", style = MaterialTheme.typography.bodySmall)
                        Text("Estimated output: ~${formatSize((selectedImage!!.size * quality / 100).coerceAtLeast(10_000))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }

                Text("Quality: $quality%", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = quality.toFloat(),
                    onValueChange = { quality = it.toInt() },
                    valueRange = 10f..100f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Smaller size", style = MaterialTheme.typography.labelSmall)
                    Text("Better quality", style = MaterialTheme.typography.labelSmall)
                }

                if (isCompressing) {
                    @Suppress("DEPRECATION")
                    LinearProgressIndicator(
                        progress = progress / 100f,
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                    Text("$progress%", style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = {
                        val img = selectedImage ?: return@Button
                        result = null
                        isCompressing = true
                        progress = 0
                        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                        scope.launch(Dispatchers.IO) {
                            val compressor = ImageCompressor(context)
                            val res = compressor.compress(img.uri, quality) { p ->
                                mainHandler.post { progress = p }
                            }
                            mainHandler.post {
                                result = res
                                isCompressing = false
                                progress = if (res.success) 100 else 0
                            }
                        }
                    },
                    enabled = !isCompressing,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isCompressing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Compressing...")
                    } else {
                        Icon(Icons.Default.Compress, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Compress Image")
                    }
                }

                result?.let { r ->
                    if (r.success) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Done!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Before: ${formatSize(r.originalSize)} → After: ${formatSize(r.compressedSize)}",
                                    style = MaterialTheme.typography.bodySmall)
                                Text(r.outputPath, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(r.message, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
        }
    }
}

private data class ImageFile(
    val uri: Uri,
    val name: String,
    val size: Long
)
