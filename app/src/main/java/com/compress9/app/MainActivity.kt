package com.compress9.app

import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.compress9.app.ui.theme.Compress9Theme
import java.io.File

enum class Screen {
    SPLASH, HOME, VIDEO, IMAGE
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Global crash logger
        val logDir = File(getExternalFilesDir(null) ?: cacheDir, "CrashLogs")
        logDir.mkdirs()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val logFile = File(logDir, "crash_${System.currentTimeMillis()}.txt")
                logFile.writeText(
                    "Thread: ${thread.name}\n" +
                    "Exception: ${throwable.javaClass.name}\n" +
                    "Message: ${throwable.message}\n" +
                    "Stack:\n${throwable.stackTraceToString()}"
                )
            } catch (_: Exception) {}
        }

        enableEdgeToEdge()
        setContent {
            Compress9Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentScreen by remember { mutableStateOf(Screen.SPLASH) }

                    when (currentScreen) {
                        Screen.SPLASH -> SplashScreen(onFinished = {
                            currentScreen = Screen.HOME
                        })
                        Screen.HOME -> HomeScreen(
                            onVideoSelected = { currentScreen = Screen.VIDEO },
                            onImageSelected = { currentScreen = Screen.IMAGE }
                        )
                        Screen.VIDEO -> VideoCompressScreen(
                            onBack = { currentScreen = Screen.HOME }
                        )
                        Screen.IMAGE -> ImageCompressScreen(
                            onBack = { currentScreen = Screen.HOME }
                        )
                    }
                }
            }
        }
    }
}
