package com.example.continuum_explorer

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.continuum_explorer.ui.FileExplorer
import com.example.continuum_explorer.ui.theme.FileExplorer2Theme
import com.example.continuum_explorer.utils.SettingsManager

class ArchiveViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize settings
        SettingsManager.init(applicationContext)

        val uri = intent.data
        if (uri == null) {
            Toast.makeText(this, "No file specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            FileExplorer2Theme {
                // FileExplorer's internal state (appState.isLoading) will handle 
                // showing the spinner during the initial parse.
                FileExplorer(initialArchiveUri = uri)
            }
        }
    }
}
