package com.troikoss.continuum_explorer.ui.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.ui.FileExplorer
import com.troikoss.continuum_explorer.ui.theme.FileExplorerTheme
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.providers.StorageProviders

class ArchiveViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        SettingsManager.init(applicationContext)
        StorageProviders.init(applicationContext)

        val uri = intent.data
        if (uri == null) {
            Toast.makeText(this, getString(R.string.msg_no_file_specified), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            FileExplorerTheme {
                // FileExplorer's internal state (appState.isLoading) will handle 
                // showing the spinner during the initial parse.
                FileExplorer(initialArchiveUri = uri)
            }
        }
    }
}
