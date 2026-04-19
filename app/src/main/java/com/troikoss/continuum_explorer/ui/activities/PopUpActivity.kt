package com.troikoss.continuum_explorer.ui.activities

import android.content.Context
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.model.NetworkConnection
import com.troikoss.continuum_explorer.model.NetworkProtocol
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.providers.LocalProvider
import com.troikoss.continuum_explorer.providers.SafProvider
import com.troikoss.continuum_explorer.ui.theme.FileExplorerTheme
import com.troikoss.continuum_explorer.managers.ArchiveSettings
import com.troikoss.continuum_explorer.managers.CollisionResult
import com.troikoss.continuum_explorer.managers.DeleteResult
import com.troikoss.continuum_explorer.managers.ExtractSettings
import com.troikoss.continuum_explorer.managers.FileOperationsManager
import com.troikoss.continuum_explorer.managers.MoveCopyResult
import com.troikoss.continuum_explorer.utils.NotificationHelper
import com.troikoss.continuum_explorer.managers.PopupType
import com.troikoss.continuum_explorer.utils.calculateSizeRecursively
import com.troikoss.continuum_explorer.utils.getDeletedAt
import com.troikoss.continuum_explorer.utils.getDeletedFrom
import com.troikoss.continuum_explorer.utils.getFileType
import com.troikoss.continuum_explorer.utils.getImageResolution
import com.troikoss.continuum_explorer.utils.getMediaDuration
import com.troikoss.continuum_explorer.utils.getVideoResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class PopUpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FileExplorerTheme {
                Box(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .padding(vertical = 24.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        val popupType by FileOperationsManager.popupType
                        
                        when (popupType) {
                            PopupType.INPUT_TEXT -> InputContent(onClose = { finish() })
                            PopupType.COLLISION -> CollisionContent()
                            PopupType.MOVE_COPY_CHOICE -> MoveCopyContent(onClose = { finish() })
                            PopupType.DELETE_CONFIRM -> DeleteConfirmContent()
                            PopupType.DELETE_PERMANENT_CONFIRM -> DeletePermanentConfirmContent()
                            PopupType.PASSWORD_INPUT -> PasswordInputContent(onClose = { finish() })
                            PopupType.EXTRACT_OPTIONS -> ExtractOptionsContent(onClose = { finish() })
                            PopupType.ARCHIVE_OPTIONS -> ArchiveOptionsContent(onClose = { finish() })
                            PopupType.SHORTCUTS -> ShortcutsContent(onClose = { finish() })
                            PopupType.PROPERTIES -> PropertiesContent(onClose = { finish() })
                            PopupType.NETWORK_CONNECTION -> NetworkConnectionContent(onClose = { finish() })
                            else -> ProgressContent(onClose = { finish() })
                        }
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        NotificationHelper.stop()
    }
}

@Composable
fun PropertiesContent(onClose: () -> Unit) {
    val targets = FileOperationsManager.propertiesTargets.value
    if (targets.isEmpty()) {
        onClose()
        return
    }

    val context = LocalContext.current
    val resources = LocalResources.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.properties),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (targets.size == 1) {
            val file = targets.first()
            val fileType = remember(file) { getFileType(file, context) }
            val formatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()) }
            val formattedDate = remember(file.lastModified) { formatter.format(file.lastModified) }

            var calculatedSize by remember { mutableStateOf<Long?>(null) }
            var calculatedSizeOnDisk by remember { mutableStateOf<Long?>(null) }
            var isCalculatingSize by remember { mutableStateOf(file.isDirectory) }
            
            var extraRes by remember { mutableStateOf<String?>(null) }
            var extraDur by remember { mutableStateOf<String?>(null) }
            var isLoadingExtra by remember { mutableStateOf(false) }

            var deletedAt by remember { mutableStateOf<Long?>(null) }
            var deletedFrom by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(file) {
                if (file.fileRef?.parentFile?.name == ".Trash") {
                    withContext(Dispatchers.IO) {
                        val at = getDeletedAt(file.name)
                        val from = getDeletedFrom(file.name)
                        withContext(Dispatchers.Main) {
                            deletedAt = at
                            deletedFrom = from
                        }
                    }
                }
                if (file.isDirectory) {
                    withContext(Dispatchers.IO) {
                        val size = calculateSizeRecursively(context, file)
                        val sizeOnDisk = calculateSizeOnDiskRecursively(file)
                        withContext(Dispatchers.Main) {
                            calculatedSize = size
                            calculatedSizeOnDisk = sizeOnDisk
                            isCalculatingSize = false
                        }
                    }
                } else {
                    calculatedSize = file.length
                    calculatedSizeOnDisk = ((file.length + 4095L) / 4096L) * 4096L
                }

                if (fileType == resources.getString(R.string.image)) {
                    isLoadingExtra = true
                    withContext(Dispatchers.IO) {
                        val res = getImageResolution(context, file)
                        withContext(Dispatchers.Main) {
                            extraRes = res
                            isLoadingExtra = false
                        }
                    }
                } else if (fileType == resources.getString(R.string.video)) {
                    isLoadingExtra = true
                    withContext(Dispatchers.IO) {
                        val res = getVideoResolution(context, file)
                        val duration = getMediaDuration(context, file)
                        withContext(Dispatchers.Main) {
                            extraRes = res
                            extraDur = duration
                            isLoadingExtra = false
                        }
                    }
                } else if (fileType == resources.getString(R.string.audio)) {
                     isLoadingExtra = true
                     withContext(Dispatchers.IO) {
                         val duration = getMediaDuration(context, file)
                         withContext(Dispatchers.Main) {
                             extraDur = duration
                             isLoadingExtra = false
                         }
                     }
                }
            }

            PropertyRow(stringResource(R.string.prop_name), file.name)
            PropertyRow(stringResource(R.string.prop_type), fileType)
            
            if (isCalculatingSize) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = stringResource(R.string.prop_size),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(80.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.calculating), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                PropertyRow(stringResource(R.string.prop_size), formatSizeWithBytes(context, calculatedSize ?: 0L))
                PropertyRow(stringResource(R.string.prop_size_on_disk), formatSizeWithBytes(context, calculatedSizeOnDisk ?: 0L))
            }
            
            PropertyRow(stringResource(R.string.prop_location), file.absolutePath)
            PropertyRow(stringResource(R.string.prop_modified), formattedDate)
            if (deletedAt != null) {
                PropertyRow(stringResource(R.string.prop_deleted), formatter.format(deletedAt))
            }
            if (deletedFrom != null) {
                PropertyRow(stringResource(R.string.prop_deleted_from), deletedFrom!!)
            }

            if (isLoadingExtra) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.prop_loading_metadata), style = MaterialTheme.typography.bodySmall)
                }
            } else if (extraRes != null || extraDur != null) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                if (extraRes != null) PropertyRow(stringResource(R.string.prop_resolution), extraRes!!)
                if (extraDur != null) PropertyRow(stringResource(R.string.prop_duration), extraDur!!)
            }
        } else {
            // Multiple files
            var totalSize by remember { mutableStateOf<Long?>(null) }
            var totalSizeOnDisk by remember { mutableStateOf<Long?>(null) }
            var isCalculatingSize by remember { mutableStateOf(true) }

            LaunchedEffect(targets) {
                withContext(Dispatchers.IO) {
                    var size = 0L
                    var sizeOnDisk = 0L
                    for (target in targets) {
                        size += calculateSizeRecursively(context, target)
                        sizeOnDisk += calculateSizeOnDiskRecursively(target)
                    }
                    withContext(Dispatchers.Main) {
                        totalSize = size
                        totalSizeOnDisk = sizeOnDisk
                        isCalculatingSize = false
                    }
                }
            }

            val dirsCount = targets.count { it.isDirectory }
            val filesCount = targets.size - dirsCount

            PropertyRow(stringResource(R.string.prop_items), stringResource(R.string.prop_selected_count, targets.size))
            PropertyRow(stringResource(R.string.prop_contains), "${stringResource(R.string.prop_folders_count, dirsCount)}, ${stringResource(R.string.prop_files_count, filesCount)}")

            if (isCalculatingSize) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = stringResource(R.string.prop_total_size),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(80.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.calculating), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                PropertyRow(stringResource(R.string.prop_total_size), formatSizeWithBytes(context, totalSize ?: 0L))
                PropertyRow(stringResource(R.string.prop_size_on_disk), formatSizeWithBytes(context, totalSizeOnDisk ?: 0L))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = onClose,
                modifier = Modifier.focusRequester(focusRequester)
            ) {
                Text(stringResource(R.string.close))
            }
        }
    }
}

@Composable
fun PropertyRow(label: String, value: String) {
    SelectionContainer {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(80.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ShortcutsContent(onClose: () -> Unit) {
    Column(
        modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.shortcuts_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            ShortcutCategory(stringResource(R.string.shortcuts_nav_selection))
            ShortcutItem(
                stringResource(R.string.shortcuts_arrow_keys),
                stringResource(R.string.shortcuts_arrow_keys_desc)
            )
            ShortcutItem(
                stringResource(R.string.shortcuts_home_end),
                stringResource(R.string.shortcuts_home_end_desc)
            )
            ShortcutItem(
                stringResource(R.string.shortcuts_page_up_down),
                stringResource(R.string.shortcuts_page_up_down_desc)
            )
            ShortcutItem(
                stringResource(R.string.shortcuts_ctrl_a),
                stringResource(R.string.shortcuts_ctrl_a_desc)
            )
            ShortcutItem(
                stringResource(R.string.shortcuts_backspace),
                stringResource(R.string.shortcuts_backspace_desc)
            )

            Spacer(modifier = Modifier.height(16.dp))
            ShortcutCategory(stringResource(R.string.shortcuts_file_ops))
            ShortcutItem(
                stringResource(R.string.shortcuts_enter),
                stringResource(R.string.shortcuts_enter_desc)
            )
            ShortcutItem(
                stringResource(R.string.shortcuts_ctrl_enter),
                stringResource(R.string.shortcuts_ctrl_enter_desc)
            )
            ShortcutItem(
                stringResource(R.string.shortcuts_shift_enter),
                stringResource(R.string.shortcuts_shift_enter_desc)
            )
            ShortcutItem(
                stringResource(R.string.shortcuts_ctrl_cvx),
                stringResource(R.string.shortcuts_ctrl_cvx_desc)
            )
            ShortcutItem(
                stringResource(R.string.shortcuts_delete),
                stringResource(R.string.shortcuts_delete_desc)
            )
            ShortcutItem(
                stringResource(R.string.shortcuts_f2),
                stringResource(R.string.shortcuts_f2_desc)
            )
            ShortcutItem(
                stringResource(R.string.shortcuts_f5),
                stringResource(R.string.shortcuts_f5_desc)
            )

            Spacer(modifier = Modifier.height(16.dp))
            ShortcutCategory(stringResource(R.string.shortcuts_app_actions))
            ShortcutItem(
                stringResource(R.string.shortcuts_ctrl_n),
                stringResource(R.string.shortcuts_ctrl_n_desc)
            )
            ShortcutItem(
                stringResource(R.string.shortcuts_ctrl_w),
                stringResource(R.string.shortcuts_ctrl_w_desc)
            )
            ShortcutItem(
                stringResource(R.string.shortcuts_ctrl_zy),
                stringResource(R.string.shortcuts_ctrl_zy_desc)
            )
            ShortcutItem(
                stringResource(R.string.shortcuts_ctrl_wheel),
                stringResource(R.string.shortcuts_ctrl_wheel_desc)
            )
            ShortcutItem(
                stringResource(R.string.shortcuts_ctrl_slash),
                stringResource(R.string.shortcuts_ctrl_slash_desc)
            )

            Spacer(modifier = Modifier.height(16.dp))
            ShortcutCategory(stringResource(R.string.shortcuts_mouse))
            ShortcutItem(
                stringResource(R.string.shortcuts_right_click),
                stringResource(R.string.shortcuts_right_click_desc)
            )
            ShortcutItem(
                stringResource(R.string.shortcuts_middle_click),
                stringResource(R.string.shortcuts_middle_click_desc)
            )
            ShortcutItem(
                stringResource(R.string.shortcuts_shift_middle_click),
                stringResource(R.string.shortcuts_shift_middle_click_desc)
            )
            ShortcutItem(
                stringResource(R.string.shortcuts_drag),
                stringResource(R.string.shortcuts_drag_desc)
            )
            ShortcutItem(
                stringResource(R.string.shortcuts_shift_drag),
                stringResource(R.string.shortcuts_shift_drag_desc)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        HorizontalDivider()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onClose) {
                Text(stringResource(R.string.close))
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveOptionsContent(onClose: () -> Unit) {
    val initialName = FileOperationsManager.initialInputText.value
    var nameState by remember(initialName) { mutableStateOf(TextFieldValue(initialName)) }
    
    var compressionMethod by remember { mutableStateOf(CompressionMethod.DEFLATE) }
    var compressionLevel by remember { mutableStateOf(CompressionLevel.NORMAL) }
    var encryptionMethod by remember { mutableStateOf(EncryptionMethod.NONE) }
    var password by remember { mutableStateOf("") }
    var deleteSource by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    val onConfirm = {
        if (nameState.text.isNotBlank() && (encryptionMethod == EncryptionMethod.NONE || password.isNotEmpty())) {
            val finalName = if (nameState.text.endsWith(".zip", ignoreCase = true)) nameState.text else "${nameState.text}.zip"
            val settings = ArchiveSettings(
                archiveName = finalName,
                compressionMethod = compressionMethod,
                compressionLevel = compressionLevel,
                encryptionMethod = encryptionMethod,
                password = if (encryptionMethod != EncryptionMethod.NONE) password else null,
                deleteSource = deleteSource,
                isCancelled = false
            )
            FileOperationsManager.onArchiveResult(settings)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = Modifier.padding(16.dp).verticalScroll(scrollState)) {
        Text(
            text = stringResource(R.string.archive_create),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = nameState,
            onValueChange = { nameState = it },
            label = { Text(stringResource(R.string.archive_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm() })
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Compression Method
        var expCompMethod by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expCompMethod,
            onExpandedChange = { expCompMethod = !expCompMethod },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = compressionMethod.name,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.archive_comp_method)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expCompMethod) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expCompMethod,
                onDismissRequest = { expCompMethod = false }
            ) {
                CompressionMethod.entries.forEach { method ->
                    DropdownMenuItem(
                        text = { Text(method.name) },
                        onClick = {
                            compressionMethod = method
                            expCompMethod = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Compression Level
        var expCompLevel by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expCompLevel,
            onExpandedChange = { expCompLevel = !expCompLevel },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = compressionLevel.name,
                onValueChange = {},
                readOnly = true,
                enabled = compressionMethod == CompressionMethod.DEFLATE,
                label = { Text(stringResource(R.string.archive_comp_level)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expCompLevel) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expCompLevel,
                onDismissRequest = { expCompLevel = false }
            ) {
                CompressionLevel.entries.forEach { level ->
                    DropdownMenuItem(
                        text = { Text(level.name) },
                        onClick = {
                            compressionLevel = level
                            expCompLevel = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Encryption Method
        var expEncMethod by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expEncMethod,
            onExpandedChange = { expEncMethod = !expEncMethod },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = encryptionMethod.name,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.archive_enc_method)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expEncMethod) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expEncMethod,
                onDismissRequest = { expEncMethod = false }
            ) {
                EncryptionMethod.entries.forEach { method ->
                    DropdownMenuItem(
                        text = { Text(method.name) },
                        onClick = {
                            encryptionMethod = method
                            expEncMethod = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (encryptionMethod != EncryptionMethod.NONE) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.archive_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm() })
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = deleteSource, onCheckedChange = { deleteSource = it })
            Text(text = stringResource(R.string.archive_delete_source), modifier = Modifier.padding(start = 8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                FileOperationsManager.onArchiveResult(ArchiveSettings("", CompressionMethod.STORE, CompressionLevel.NORMAL, EncryptionMethod.NONE, null, false, true))
                onClose()
            }) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onConfirm,
                enabled = nameState.text.isNotBlank() && (encryptionMethod == EncryptionMethod.NONE || password.isNotEmpty()),
                modifier = Modifier.focusRequester(focusRequester)
            ) {
                Text(stringResource(R.string.archive_button))
            }
        }
    }
}

@Composable
fun ExtractOptionsContent(onClose: () -> Unit) {
    val title = FileOperationsManager.dialogTitle.value
    var toSeparateFolder by remember { mutableStateOf(true) }
    var deleteSource by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.archive_extract),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.archive_extract_options, title),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = toSeparateFolder,
                onClick = { toSeparateFolder = true }
            )
            Text(
                text = stringResource(R.string.archive_extract_separate),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = !toSeparateFolder,
                onClick = { toSeparateFolder = false }
            )
            Text(
                text = stringResource(R.string.archive_extract_current),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = deleteSource,
                onCheckedChange = { deleteSource = it }
            )
            Text(
                text = stringResource(R.string.archive_extract_delete_source),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { 
                FileOperationsManager.onExtractResult(ExtractSettings(false, false, true))
                onClose() 
            }) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { 
                    FileOperationsManager.onExtractResult(ExtractSettings(toSeparateFolder, deleteSource))
                },
                modifier = Modifier.focusRequester(focusRequester)
            ) {
                Text(stringResource(R.string.extract_button))
            }
        }
    }
}

@Composable
fun PasswordInputContent(onClose: () -> Unit) {
    val title = FileOperationsManager.dialogTitle.value
    var password by remember { mutableStateOf("") }
    val isOperating = FileOperationsManager.isOperating.value
    val focusRequester = remember { FocusRequester() }

    val onConfirm = {
        FileOperationsManager.onPasswordResult(password)
        if (!isOperating) {
            onClose()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.archive_encrypted_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.archive_enter_password, title),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.archive_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm() })
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { 
                FileOperationsManager.onPasswordResult(null)
                onClose() 
            }) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier.focusRequester(focusRequester)
            ) {
                Text(stringResource(R.string.ok))
            }
        }
    }
}

@Composable
fun InputContent(onClose: () -> Unit) {
    val title = FileOperationsManager.dialogTitle.value
    val buttonLabel = FileOperationsManager.confirmButtonText.value
    val initialText = FileOperationsManager.initialInputText.value
    val onConfirmAction = FileOperationsManager.onTextInputConfirm
    val focusRequester = remember { FocusRequester() }

    var textState by remember(initialText) {
        val lastDotIndex = initialText.lastIndexOf('.')
        val selectionEnd = if (lastDotIndex > 0) lastDotIndex else initialText.length
        mutableStateOf(TextFieldValue(text = initialText, selection = TextRange(0, selectionEnd)))
    }

    val onConfirm = {
        if (textState.text.isNotBlank()) {
            onConfirmAction?.invoke(textState.text)
            // If the action started an operation (e.g. rename), keep the popup alive so it
            // can transition to PROGRESS / COLLISION. For non-operation confirms (create
            // folder/file), isOperating stays false and we close normally.
            if (!FileOperationsManager.isOperating.value) {
                onClose()
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = textState,
            onValueChange = { textState = it },
            label = { Text(stringResource(R.string.archive_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm() })
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onConfirm,
                enabled = textState.text.isNotBlank()
            ) {
                Text(buttonLabel)
            }
        }
    }
}


@Composable
fun CollisionContent() {
    val fileName = FileOperationsManager.collisionFileName.value
    var rememberSelection by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val isDirectory = FileOperationsManager.collisionIsDirectory.value

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = stringResource(R.string.conflict_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.conflict_message, fileName), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = rememberSelection, onCheckedChange = { rememberSelection = it })
            Text(text = stringResource(R.string.conflict_remember), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(16.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = { FileOperationsManager.onCollisionChoice(CollisionResult.CANCEL, false) }) { Text(stringResource(R.string.cancel)) }
            if (isDirectory) {
                Button(onClick = { FileOperationsManager.onCollisionChoice(CollisionResult.MERGE, rememberSelection) }) {
                    Text(stringResource(R.string.conflict_merge))
                }
            }
            Button(onClick = { FileOperationsManager.onCollisionChoice(CollisionResult.KEEP_BOTH, rememberSelection) }) { Text(stringResource(R.string.conflict_keep_both)) }
            Button(
                onClick = { FileOperationsManager.onCollisionChoice(CollisionResult.REPLACE, rememberSelection) },
                modifier = Modifier.focusRequester(focusRequester)
            ) { Text(stringResource(R.string.conflict_replace)) }
        }
    }
}

@Composable
fun MoveCopyContent(onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.move_or_copy_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.move_or_copy_message),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            TextButton(onClick = {
                FileOperationsManager.onMoveCopyChoice(MoveCopyResult.CANCEL)
                onClose()
            }) {
                Text(stringResource(R.string.cancel))
            }
            Button(onClick = {
                FileOperationsManager.onMoveCopyChoice(MoveCopyResult.COPY)
            }) {
                Text(stringResource(R.string.menu_copy))
            }
            Button(
                onClick = {
                    FileOperationsManager.onMoveCopyChoice(MoveCopyResult.MOVE)
                },
                modifier = Modifier.focusRequester(focusRequester)
            ) {
                Text(stringResource(R.string.menu_move))
            }
        }
    }
}

@Composable
fun DeleteConfirmContent() {
    val count = FileOperationsManager.deleteItemCount.intValue
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = stringResource(R.string.delete_confirm_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        val itemText = if (count == 1) stringResource(R.string.delete_item_this) else stringResource(R.string.delete_items_count, count)
        Text(text = stringResource(R.string.delete_confirm_message, itemText), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TextButton(
                onClick = { FileOperationsManager.onDeleteChoice(DeleteResult.CANCEL) },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(stringResource(R.string.cancel), maxLines = 1)
            }
            
            Button(
                onClick = { FileOperationsManager.onDeleteChoice(DeleteResult.RECYCLE) },
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.focusRequester(focusRequester)
            ) {
                Text(stringResource(R.string.delete_recycle_bin), maxLines = 1, fontSize = 13.sp)
            }
            
            Button(
                onClick = { FileOperationsManager.onDeleteChoice(DeleteResult.PERMANENT) },
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text(stringResource(R.string.delete_permanently), maxLines = 1, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun DeletePermanentConfirmContent() {
    val count = FileOperationsManager.deleteItemCount.intValue
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = stringResource(R.string.delete_permanent_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        val itemText = if (count == 1) stringResource(R.string.delete_item_this) else stringResource(R.string.delete_items_count, count)
        Text(text = stringResource(R.string.delete_permanent_message, itemText), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { FileOperationsManager.onDeleteChoice(DeleteResult.CANCEL) }) { Text(stringResource(R.string.no)) }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { FileOperationsManager.onDeleteChoice(DeleteResult.PERMANENT) },
                modifier = Modifier.focusRequester(focusRequester)
            ) { Text(stringResource(R.string.yes)) }
        }
    }
}


@Composable
fun ProgressContent (onClose: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val progress = FileOperationsManager.progress.floatValue
    val isRunning = FileOperationsManager.isOperating.value
    val currentFileName = FileOperationsManager.currentFileName.value ?: ""
    val itemsTotal = FileOperationsManager.itemsTotal.intValue
    val itemsProcessed = FileOperationsManager.itemsProcessed.intValue
    val totalSize = FileOperationsManager.totalSize.longValue
    val processedSize = FileOperationsManager.processedSize.longValue
    val speedBytesPerSec = FileOperationsManager.currentSpeed.longValue
    val timeRemainingMillis = FileOperationsManager.timeRemaining.longValue
    val focusRequester = remember { FocusRequester() }

    val itemsRemaining = if (itemsTotal > itemsProcessed) itemsTotal - itemsProcessed else 0
    val sizeRemainingString = remember(totalSize, processedSize) {
        val remaining = if (totalSize > processedSize) totalSize - processedSize else 0L
        Formatter.formatFileSize(context, remaining)
    }
    
    val timeString = remember(timeRemainingMillis, isRunning) {
        if (!isRunning) resources.getString(R.string.done)
        else if (timeRemainingMillis <= 0) resources.getString(R.string.calculating)
        else {
            val seconds = timeRemainingMillis / 1000
            if (seconds > 60) resources.getString(R.string.op_min_remaining, seconds / 60) else resources.getString(R.string.op_sec_remaining, seconds)
        }
    }

    LaunchedEffect(isRunning) { 
        if (!isRunning) { 
            focusRequester.requestFocus()
            delay(1500)
            onClose() 
        } 
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = FileOperationsManager.getTitleText(context), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().height(18.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        DetailRow(label = stringResource(R.string.prop_name), value = currentFileName)
        DetailRow(label = stringResource(R.string.op_time_remaining), value = timeString)
        DetailRow(label = stringResource(R.string.op_items_remaining), value = "$itemsRemaining ($sizeRemainingString)")
        DetailRow(label = stringResource(R.string.op_speed), value = "${Formatter.formatFileSize(context, speedBytesPerSec)}/${resources.getString(R.string.op_sec)}")
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!isRunning) {
                Button(
                    onClick = onClose,
                    modifier = Modifier.focusRequester(focusRequester)
                ) { Text(stringResource(R.string.close)) }
            } else {
                TextButton(onClick = { NotificationHelper.start(context); onClose() }) { Text(stringResource(R.string.op_background)) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { FileOperationsManager.cancel() }) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(100.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkConnectionContent(onClose: () -> Unit) {
    val existing = FileOperationsManager.networkConnectionToEdit.value
    var protocol by remember { mutableStateOf(existing?.protocol ?: NetworkProtocol.FTP) }
    var displayName by remember { mutableStateOf(existing?.displayName ?: "") }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var port by remember { mutableStateOf(existing?.port?.takeIf { it > 0 }?.toString() ?: protocol.defaultPort.toString()) }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var password by remember { mutableStateOf(existing?.password ?: "") }
    var remotePath by remember { mutableStateOf(existing?.remotePath ?: "/") }
    var useTls by remember { mutableStateOf(existing?.useTls ?: false) }
    var ftpPassiveMode by remember { mutableStateOf(existing?.ftpPassiveMode ?: true) }
    var rootUrl by remember { mutableStateOf(existing?.rootUrl ?: "") }
    var acceptUntrustedCerts by remember { mutableStateOf(existing?.acceptUntrustedCerts ?: false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    val isGoogleDrive = protocol == NetworkProtocol.GOOGLE_DRIVE
    val isFtp = protocol == NetworkProtocol.FTP
    val isWebDav = protocol == NetworkProtocol.WEBDAV
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val onSave = {
        val connection = NetworkConnection(
            id = existing?.id ?: UUID.randomUUID().toString(),
            protocol = protocol,
            displayName = displayName.trim(),
            host = host.trim(),
            port = port.toIntOrNull() ?: protocol.defaultPort,
            username = username.trim(),
            password = password,
            remotePath = remotePath.trim().ifEmpty { "/" },
            useTls = useTls,
            ftpPassiveMode = ftpPassiveMode,
            rootUrl = rootUrl.trim(),
            acceptUntrustedCerts = acceptUntrustedCerts
        )
        FileOperationsManager.onNetworkConnectionResult(connection)
        onClose()
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
    ) {
        Text(
            text = stringResource(if (existing == null) R.string.nav_add_network_storage else R.string.nav_network_edit),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        var expProtocol by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expProtocol,
            onExpandedChange = { expProtocol = !expProtocol },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = protocol.name.replace('_', ' '),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.nav_network_protocol)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expProtocol) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expProtocol,
                onDismissRequest = { expProtocol = false }
            ) {
                NetworkProtocol.entries.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.name.replace('_', ' ')) },
                        onClick = {
                            protocol = p
                            port = p.defaultPort.takeIf { it > 0 }?.toString() ?: ""
                            expProtocol = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text(stringResource(R.string.nav_network_display_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        if (!isGoogleDrive) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text(stringResource(R.string.nav_network_host)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text(stringResource(R.string.nav_network_port)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.nav_network_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.nav_network_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = remotePath,
                onValueChange = { remotePath = it },
                label = { Text(stringResource(R.string.nav_network_remote_path)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (displayName.isNotBlank()) onSave() })
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (isFtp) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = useTls, onCheckedChange = { useTls = it })
                    Text(stringResource(R.string.nav_network_use_tls))
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = ftpPassiveMode, onCheckedChange = { ftpPassiveMode = it })
                    Text(stringResource(R.string.nav_network_passive_mode))
                }
            }
            if (isWebDav) {
                OutlinedTextField(
                    value = rootUrl,
                    onValueChange = { rootUrl = it },
                    label = { Text(stringResource(R.string.nav_network_root_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if ((isFtp && useTls) || isWebDav) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = acceptUntrustedCerts, onCheckedChange = { acceptUntrustedCerts = it })
                    Text(stringResource(R.string.nav_network_accept_untrusted))
                }
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.nav_network_google_drive_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!isGoogleDrive && (isFtp || isWebDav)) {
            Spacer(modifier = Modifier.height(8.dp))
            val canTest = (host.isNotBlank() || rootUrl.isNotBlank()) && !isTesting
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        isTesting = true
                        testResult = null
                        val conn = NetworkConnection(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            protocol = protocol,
                            displayName = displayName.trim(),
                            host = host.trim(),
                            port = port.toIntOrNull() ?: protocol.defaultPort,
                            username = username.trim(),
                            password = password,
                            remotePath = remotePath.trim().ifEmpty { "/" },
                            useTls = useTls, ftpPassiveMode = ftpPassiveMode,
                            rootUrl = rootUrl.trim(), acceptUntrustedCerts = acceptUntrustedCerts
                        )
                        scope.launch {
                            val result = FileOperationsManager.testNetworkConnection(conn)
                            isTesting = false
                            testResult = if (result.isSuccess) "✓ Connected" else "✗ ${result.exceptionOrNull()?.message ?: "Failed"}"
                        }
                    },
                    enabled = canTest
                ) {
                    if (isTesting) {
                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(stringResource(R.string.nav_network_test))
                }
                testResult?.let {
                    Spacer(Modifier.width(12.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                FileOperationsManager.onNetworkConnectionResult(null)
                onClose()
            }) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSave,
                enabled = displayName.isNotBlank() && (isGoogleDrive || host.isNotBlank())
            ) {
                Text(stringResource(R.string.nav_network_save))
            }
        }
    }
}

// Helper to format text like: "1.2 MB (1,234,567 bytes)"
private fun formatSizeWithBytes(context: Context, sizeInBytes: Long): String {
    val formattedSize = Formatter.formatFileSize(context, sizeInBytes)
    val exactBytes = NumberFormat.getInstance().format(sizeInBytes)
    return "$formattedSize ($exactBytes ${context.getString(R.string.prop_bytes)})"
}

// Helper to calculate actual space taken on the storage drive (using standard 4KB blocks)
private fun calculateSizeOnDiskRecursively(file: UniversalFile): Long {


    if (!file.isDirectory) {
        val length = file.length
        return if (length == 0L) 0L else ((length + 4095L) / 4096L) * 4096L
    }

    var total = 4096L // Folders themselves take up at least one block of space

    // 1. If it's a standard local folder
    val popupFileRef = file.fileRef
    val popupDocRef = file.documentFileRef
    if (popupFileRef != null) {
        popupFileRef.listFiles()?.forEach { child ->
            val tempFile = UniversalFile(
                name = child.name,
                isDirectory = child.isDirectory,
                lastModified = child.lastModified(),
                length = child.length(),
                provider = LocalProvider,
                providerId = child.absolutePath,
                parentId = child.parentFile?.absolutePath,
            )
            total += calculateSizeOnDiskRecursively(tempFile)
        }
    } else if (popupDocRef != null) {
        popupDocRef.listFiles().forEach { child ->
            val tempFile = UniversalFile(
                name = child.name ?: "Unknown",
                isDirectory = child.isDirectory,
                lastModified = child.lastModified(),
                length = child.length(),
                provider = SafProvider,
                providerId = child.uri.toString(),
                parentId = child.parentFile?.uri?.toString(),
                mimeType = child.type,
            )
            total += calculateSizeOnDiskRecursively(tempFile)
        }
    }
    return total
}