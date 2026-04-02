package com.troikoss.continuum_explorer.ui.activities

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.ui.theme.FileExplorerTheme
import com.troikoss.continuum_explorer.managers.DeleteBehavior
import com.troikoss.continuum_explorer.managers.DetailsMode
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.managers.ThemeMode

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FileExplorerTheme {
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
    val language = SettingsManager.language.value
    val detailsMode = SettingsManager.detailsMode.value
    val isCommandBarVisible = SettingsManager.isCommandBarVisible.value
    val showHiddenFiles = SettingsManager.showHiddenFiles.value

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showShortcutsDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                stringResource(R.string.settings_appearance),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_theme)) },
                supportingContent = {
                    val text = when (themeMode) {
                        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                    }
                    Text(text)
                },
                modifier = Modifier.clickable { showThemeDialog = true }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_language)) },
                supportingContent = {
                    val text = when (language) {
                        "tr" -> stringResource(R.string.settings_language_turkish)
                        "en" -> stringResource(R.string.settings_language_english)
                        "fr" -> stringResource(R.string.settings_language_french)
                        "pt" -> stringResource(R.string.settings_language_portuguese)
                        "es" -> stringResource(R.string.settings_language_spanish)
                        else -> stringResource(R.string.settings_language_system)
                    }
                    Text(text)
                },
                modifier = Modifier.clickable { showLanguageDialog = true }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_details_large)) },
                supportingContent = {
                    val text = when (detailsMode) {
                        DetailsMode.OFF -> stringResource(R.string.settings_details_hidden)
                        DetailsMode.PANE -> stringResource(R.string.settings_details_pane)
                        DetailsMode.BAR -> stringResource(R.string.settings_details_bar)
                    }
                    Text(text)
                },
                modifier = Modifier.clickable { showDetailsDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_show_command_bar)) },
                supportingContent = { Text(stringResource(R.string.settings_show_command_bar_desc)) },
                trailingContent = {
                    Switch(
                        checked = isCommandBarVisible,
                        onCheckedChange = { SettingsManager.setCommandBarVisible(context, it) }
                    )
                }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_show_hidden_files)) },
                supportingContent = { Text(stringResource(R.string.settings_show_hidden_files_desc)) },
                trailingContent = {
                    Switch(
                        checked = showHiddenFiles,
                        onCheckedChange = { SettingsManager.setShowHiddenFiles(context, it) }
                    )
                }
            )

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_file_ops), 
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )
            
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_deletion_behavior)) },
                supportingContent = { 
                    val text = when(deleteBehavior) {
                        DeleteBehavior.ASK -> stringResource(R.string.settings_del_ask)
                        DeleteBehavior.RECYCLE -> stringResource(R.string.settings_del_recycle)
                        DeleteBehavior.PERMANENT -> stringResource(R.string.settings_del_permanent)
                    }
                    Text(text)
                },
                modifier = Modifier.clickable { showDeleteDialog = true }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_internal_archive)) },
                supportingContent = { Text(stringResource(R.string.settings_internal_archive_desc)) },
                trailingContent = {
                    Switch(
                        checked = isDefaultArchiveViewerEnabled,
                        onCheckedChange = { SettingsManager.setDefaultArchiveViewerEnabled(context, it) }
                    )
                }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text(stringResource(R.string.shortcuts_title)) },
                supportingContent = { Text(stringResource(R.string.settings_shortcuts_desc)) },
                leadingContent = { Icon(Icons.Default.Keyboard, contentDescription = null) },
                modifier = Modifier.clickable { showShortcutsDialog = true }
            )

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text(stringResource(R.string.settings_choose_del_behavior)) },
                    text = {
                        Column {
                            OptionItem(
                                label = stringResource(R.string.settings_del_ask),
                                selected = deleteBehavior == DeleteBehavior.ASK,
                                onClick = { 
                                    SettingsManager.setDeleteBehavior(context, DeleteBehavior.ASK)
                                    showDeleteDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_del_recycle),
                                selected = deleteBehavior == DeleteBehavior.RECYCLE,
                                onClick = { 
                                    SettingsManager.setDeleteBehavior(context, DeleteBehavior.RECYCLE)
                                    showDeleteDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_del_permanent),
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
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            if (showThemeDialog) {
                AlertDialog(
                    onDismissRequest = { showThemeDialog = false },
                    title = { Text(stringResource(R.string.settings_choose_theme)) },
                    text = {
                        Column {
                            OptionItem(
                                label = stringResource(R.string.settings_theme_system),
                                selected = themeMode == ThemeMode.SYSTEM,
                                onClick = {
                                    SettingsManager.setThemeMode(context, ThemeMode.SYSTEM)
                                    showThemeDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_theme_light),
                                selected = themeMode == ThemeMode.LIGHT,
                                onClick = {
                                    SettingsManager.setThemeMode(context, ThemeMode.LIGHT)
                                    showThemeDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_theme_dark),
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
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            if (showLanguageDialog) {
                AlertDialog(
                    onDismissRequest = { showLanguageDialog = false },
                    title = { Text(stringResource(R.string.settings_choose_language)) },
                    text = {
                        Column {
                            OptionItem(
                                label = stringResource(R.string.settings_language_system),
                                selected = language == "system",
                                onClick = {
                                    SettingsManager.setLanguage(context, "system")
                                    showLanguageDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_language_english),
                                selected = language == "en",
                                onClick = {
                                    SettingsManager.setLanguage(context, "en")
                                    showLanguageDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_language_french),
                                selected = language == "fr",
                                onClick = {
                                    SettingsManager.setLanguage(context, "fr")
                                    showLanguageDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_language_portuguese),
                                selected = language == "pt",
                                onClick = {
                                    SettingsManager.setLanguage(context, "pt")
                                    showLanguageDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_language_spanish),
                                selected = language == "es",
                                onClick = {
                                    SettingsManager.setLanguage(context, "es")
                                    showLanguageDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_language_turkish),
                                selected = language == "tr",
                                onClick = {
                                    SettingsManager.setLanguage(context, "tr")
                                    showLanguageDialog = false
                                }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showLanguageDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            if (showDetailsDialog) {
                AlertDialog(
                    onDismissRequest = { showDetailsDialog = false },
                    title = { Text(stringResource(R.string.settings_choose_details_mode)) },
                    text = {
                        Column {
                            OptionItem(
                                label = stringResource(R.string.settings_details_hidden),
                                selected = detailsMode == DetailsMode.OFF,
                                onClick = {
                                    SettingsManager.setDetailsMode(context, DetailsMode.OFF)
                                    showDetailsDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_details_pane),
                                selected = detailsMode == DetailsMode.PANE,
                                onClick = {
                                    SettingsManager.setDetailsMode(context, DetailsMode.PANE)
                                    showDetailsDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_details_bar),
                                selected = detailsMode == DetailsMode.BAR,
                                onClick = {
                                    SettingsManager.setDetailsMode(context, DetailsMode.BAR)
                                    showDetailsDialog = false
                                }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDetailsDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            if (showShortcutsDialog) {
                AlertDialog(
                    onDismissRequest = { showShortcutsDialog = false },
                    title = { Text(stringResource(R.string.shortcuts_title)) },
                    text = {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .fillMaxWidth()
                        ) {
                            ShortcutCategory(stringResource(R.string.shortcuts_nav_selection))
                            ShortcutItem(stringResource(R.string.shortcuts_arrow_keys), stringResource(R.string.shortcuts_arrow_keys_desc))
                            ShortcutItem(stringResource(R.string.shortcuts_home_end), stringResource(R.string.shortcuts_home_end_desc))
                            ShortcutItem(stringResource(R.string.shortcuts_page_up_down), stringResource(R.string.shortcuts_page_up_down_desc))
                            ShortcutItem(stringResource(R.string.shortcuts_ctrl_a), stringResource(R.string.shortcuts_ctrl_a_desc))
                            ShortcutItem(stringResource(R.string.shortcuts_backspace), stringResource(R.string.shortcuts_backspace_desc))

                            Spacer(modifier = Modifier.height(16.dp))
                            ShortcutCategory(stringResource(R.string.shortcuts_file_ops))
                            ShortcutItem(stringResource(R.string.shortcuts_enter), stringResource(R.string.shortcuts_enter_desc))
                            ShortcutItem(stringResource(R.string.shortcuts_ctrl_enter), stringResource(R.string.shortcuts_ctrl_enter_desc))
                            ShortcutItem(stringResource(R.string.shortcuts_shift_enter), stringResource(R.string.shortcuts_shift_enter_desc))
                            ShortcutItem(stringResource(R.string.shortcuts_ctrl_cvx), stringResource(R.string.shortcuts_ctrl_cvx_desc))
                            ShortcutItem(stringResource(R.string.shortcuts_delete), stringResource(R.string.shortcuts_delete_desc))
                            ShortcutItem(stringResource(R.string.shortcuts_f2), stringResource(R.string.shortcuts_f2_desc))
                            ShortcutItem(stringResource(R.string.shortcuts_f5), stringResource(R.string.shortcuts_f5_desc))

                            Spacer(modifier = Modifier.height(16.dp))
                            ShortcutCategory(stringResource(R.string.shortcuts_app_actions))
                            ShortcutItem(stringResource(R.string.shortcuts_ctrl_n), stringResource(R.string.shortcuts_ctrl_n_desc))
                            ShortcutItem(stringResource(R.string.shortcuts_ctrl_w), stringResource(R.string.shortcuts_ctrl_w_desc))
                            ShortcutItem(stringResource(R.string.shortcuts_ctrl_zy), stringResource(R.string.shortcuts_ctrl_zy_desc))
                            ShortcutItem(stringResource(R.string.shortcuts_ctrl_wheel), stringResource(R.string.shortcuts_ctrl_wheel_desc))
                            ShortcutItem(stringResource(R.string.shortcuts_ctrl_slash), stringResource(R.string.shortcuts_ctrl_slash_desc))

                            Spacer(modifier = Modifier.height(16.dp))
                            ShortcutCategory(stringResource(R.string.shortcuts_mouse))
                            ShortcutItem(stringResource(R.string.shortcuts_right_click), stringResource(R.string.shortcuts_right_click_desc))
                            ShortcutItem(stringResource(R.string.shortcuts_middle_click), stringResource(R.string.shortcuts_middle_click_desc))
                            ShortcutItem(stringResource(R.string.shortcuts_shift_middle_click), stringResource(R.string.shortcuts_shift_middle_click_desc))
                            ShortcutItem(stringResource(R.string.shortcuts_drag), stringResource(R.string.shortcuts_drag_desc))
                            ShortcutItem(stringResource(R.string.shortcuts_shift_drag), stringResource(R.string.shortcuts_shift_drag_desc))
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showShortcutsDialog = false }) {
                            Text(stringResource(R.string.close))
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
