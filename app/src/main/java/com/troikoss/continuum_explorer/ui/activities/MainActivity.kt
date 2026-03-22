package com.troikoss.continuum_explorer.ui.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.troikoss.continuum_explorer.ui.FileExplorer
import com.troikoss.continuum_explorer.ui.theme.FileExplorerTheme
import com.troikoss.continuum_explorer.managers.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import android.Manifest
import coil.Coil

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // The user granted the permission. Notifications will work!
        } else {
            // The user denied the permission.
            // Optional: You could show a message here explaining why you need it later.
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // If not already granted, show the system popup asking for permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Request "All Files Access" permission on Android 11+ (API 30+) exactly once on create
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${packageName}")
                startActivity(intent)
            }
        }

        // Initialize settings
        SettingsManager.init(applicationContext)

        val isNewWindow = intent.action == "com.troikoss.continuum_explorer.OPEN_NEW_WINDOW"

        val initialPath = if (isNewWindow) null else intent.getStringExtra("path")
        val initialUri = if (isNewWindow) null else intent.getStringExtra("uri")
        val initialArchive = if (isNewWindow) null else {
            val path = intent.getStringExtra("archivePath")
            if (path != null) File(path) else null
        }

        // Build ImageLoader asynchronously to prevent main thread lag on startup
        lifecycleScope.launch(Dispatchers.IO) {
            val videoImageLoader = ImageLoader.Builder(this@MainActivity)
                .components {
                    add(VideoFrameDecoder.Factory())
                }
                .crossfade(true)
                .build()

            // Set this as the global loader
            Coil.setImageLoader(videoImageLoader)
        }

        setContent {
            FileExplorerTheme {
                FileExplorer(initialPath = initialPath, initialUri = initialUri, initialArchive = initialArchive)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        // Handle "New Window" requests sent to an existing instance
        if (intent.action == "com.troikoss.continuum_explorer.OPEN_NEW_WINDOW") {
            val newWindowIntent = Intent(intent).apply {
                // Force a new task
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
            }
            startActivity(newWindowIntent)
            return
        }
    }
}