package com.example.continuum_explorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.continuum_explorer.ui.theme.FileExplorer2Theme
import com.example.continuum_explorer.utils.DeleteBehavior
import com.example.continuum_explorer.utils.SettingsManager
import com.example.continuum_explorer.utils.ThemeMode

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FileExplorer2Theme {
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val deleteBehavior = SettingsManager.deleteBehavior.value
    val isDefaultArchiveViewerEnabled = SettingsManager.isDefaultArchiveViewerEnabled.value
    val themeMode = SettingsManager.themeMode.value
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showShortcutsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Appearance",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )

            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = {
                    val text = when (themeMode) {
                        ThemeMode.SYSTEM -> "System default"
                        ThemeMode.LIGHT -> "Light mode"
                        ThemeMode.DARK -> "Dark mode"
                    }
                    Text(text)
                },
                modifier = Modifier.clickable { showThemeDialog = true }
            )

            ListItem(
                headlineContent = { Text("Shortcut Cheatsheet") },
                supportingContent = { Text("View available keyboard and mouse shortcuts") },
                leadingContent = { Icon(Icons.Default.Keyboard, contentDescription = null) },
                modifier = Modifier.clickable { showShortcutsDialog = true }
            )

            HorizontalDivider()

            Text(
                "File Operations", 
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )
            
            ListItem(
                headlineContent = { Text("Deletion Behavior") },
                supportingContent = { 
                    val text = when(deleteBehavior) {
                        DeleteBehavior.ASK -> "Ask each time"
                        DeleteBehavior.RECYCLE -> "Move to Recycle Bin"
                        DeleteBehavior.PERMANENT -> "Delete permanently"
                    }
                    Text(text)
                },
                modifier = Modifier.clickable { showDeleteDialog = true }
            )

            ListItem(
                headlineContent = { Text("Use Internal Archive Viewer") },
                supportingContent = { Text("Open zip files directly inside the app instead of launching external apps.") },
                trailingContent = {
                    Switch(
                        checked = isDefaultArchiveViewerEnabled,
                        onCheckedChange = { SettingsManager.setDefaultArchiveViewerEnabled(context, it) }
                    )
                }
            )

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Choose Deletion Behavior") },
                    text = {
                        Column {
                            OptionItem(
                                label = "Ask each time",
                                selected = deleteBehavior == DeleteBehavior.ASK,
                                onClick = { 
                                    SettingsManager.setDeleteBehavior(context, DeleteBehavior.ASK)
                                    showDeleteDialog = false
                                }
                            )
                            OptionItem(
                                label = "Move to Recycle Bin",
                                selected = deleteBehavior == DeleteBehavior.RECYCLE,
                                onClick = { 
                                    SettingsManager.setDeleteBehavior(context, DeleteBehavior.RECYCLE)
                                    showDeleteDialog = false
                                }
                            )
                            OptionItem(
                                label = "Delete permanently",
                                selected = deleteBehavior == DeleteBehavior.PERMANENT,
                                onClick = { 
                                    SettingsManager.setDeleteBehavior(context, DeleteBehavior.PERMANENT)
                                    showDeleteDialog = false
                                }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showThemeDialog) {
                AlertDialog(
                    onDismissRequest = { showThemeDialog = false },
                    title = { Text("Choose Theme") },
                    text = {
                        Column {
                            OptionItem(
                                label = "System default",
                                selected = themeMode == ThemeMode.SYSTEM,
                                onClick = {
                                    SettingsManager.setThemeMode(context, ThemeMode.SYSTEM)
                                    showThemeDialog = false
                                }
                            )
                            OptionItem(
                                label = "Light mode",
                                selected = themeMode == ThemeMode.LIGHT,
                                onClick = {
                                    SettingsManager.setThemeMode(context, ThemeMode.LIGHT)
                                    showThemeDialog = false
                                }
                            )
                            OptionItem(
                                label = "Dark mode",
                                selected = themeMode == ThemeMode.DARK,
                                onClick = {
                                    SettingsManager.setThemeMode(context, ThemeMode.DARK)
                                    showThemeDialog = false
                                }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showThemeDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showShortcutsDialog) {
                AlertDialog(
                    onDismissRequest = { showShortcutsDialog = false },
                    title = { Text("Shortcuts Cheatsheet") },
                    text = {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .fillMaxWidth()
                        ) {
                            ShortcutCategory("Navigation & Selection")
                            ShortcutItem("Arrow Keys", "Move selection")
                            ShortcutItem("Home / End", "Go to start / end of list")
                            ShortcutItem("Page Up / Down", "Scroll page up / down")
                            ShortcutItem("Ctrl + A", "Select all")
                            ShortcutItem("Backspace", "Go back to parent folder")

                            Spacer(modifier = Modifier.height(16.dp))
                            ShortcutCategory("File Operations")
                            ShortcutItem("Enter", "Open file or folder")
                            ShortcutItem("Ctrl + Enter", "Open in new tab")
                            ShortcutItem("Shift + Enter", "Open in new window")
                            ShortcutItem("Ctrl + C / X / V", "Copy / Cut / Paste")
                            ShortcutItem("Delete", "Delete (Shift + Delete for permanent)")
                            ShortcutItem("F2", "Rename")
                            ShortcutItem("F5", "Refresh")

                            Spacer(modifier = Modifier.height(16.dp))
                            ShortcutCategory("App Actions")
                            ShortcutItem("Ctrl + N", "New window")
                            ShortcutItem("Ctrl + W", "Close window")
                            ShortcutItem("Ctrl + Z / Y", "Undo / Redo")
                            ShortcutItem("Ctrl + Mouse Wheel", "Zoom in / out")
                            ShortcutItem("Ctrl + /", "Shortcut Cheatsheet")

                            Spacer(modifier = Modifier.height(16.dp))
                            ShortcutCategory("Mouse")
                            ShortcutItem("Right Click", "Context menu")
                            ShortcutItem("Middle Click", "Open in new tab")
                            ShortcutItem("Shift + Middle Click", "Open in new window")
                            ShortcutItem("Drag", "Copy")
                            ShortcutItem("Shift + Drag", "Move")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showShortcutsDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ShortcutCategory(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
fun ShortcutItem(keys: String, action: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = keys, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(text = action, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OptionItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
