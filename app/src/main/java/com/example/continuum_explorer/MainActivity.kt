package com.example.continuum_explorer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.example.continuum_explorer.ui.FileExplorer
import com.example.continuum_explorer.ui.theme.FileExplorer2Theme
import com.example.continuum_explorer.utils.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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

        val initialPath = intent.getStringExtra("path")
        val initialUri = intent.getStringExtra("uri")
        val initialArchivePath = intent.getStringExtra("archivePath")
        val initialArchive = if (initialArchivePath != null) File(initialArchivePath) else null

        // Build ImageLoader asynchronously to prevent main thread lag on startup
        lifecycleScope.launch(Dispatchers.IO) {
            val videoImageLoader = ImageLoader.Builder(this@MainActivity)
                .components {
                    add(VideoFrameDecoder.Factory())
                }
                .crossfade(true)
                .build()

            // Set this as the global loader
            coil.Coil.setImageLoader(videoImageLoader)
        }

        setContent {
            FileExplorer2Theme {
                FileExplorer(initialPath = initialPath, initialUri = initialUri, initialArchive = initialArchive)
            }
        }
    }
}