package com.example.continuum_explorer

import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.continuum_explorer.ui.theme.FileExplorer2Theme
import com.example.continuum_explorer.utils.ArchiveSettings
import com.example.continuum_explorer.utils.CollisionResult
import com.example.continuum_explorer.utils.DeleteResult
import com.example.continuum_explorer.utils.ExtractSettings
import com.example.continuum_explorer.utils.FileOperationsManager
import com.example.continuum_explorer.utils.NotificationHelper
import com.example.continuum_explorer.utils.PopupType
import com.example.continuum_explorer.utils.calculateSizeRecursively
import com.example.continuum_explorer.utils.getFileType
import com.example.continuum_explorer.utils.getImageResolution
import com.example.continuum_explorer.utils.getMediaDuration
import com.example.continuum_explorer.utils.getVideoResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.text.SimpleDateFormat
import java.util.Locale

class PopUpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FileExplorer2Theme {
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
                            PopupType.DELETE_CONFIRM -> DeleteConfirmContent()
                            PopupType.DELETE_PERMANENT_CONFIRM -> DeletePermanentConfirmContent()
                            PopupType.PASSWORD_INPUT -> PasswordInputContent(onClose = { finish() })
                            PopupType.EXTRACT_OPTIONS -> ExtractOptionsContent(onClose = { finish() })
                            PopupType.ARCHIVE_OPTIONS -> ArchiveOptionsContent(onClose = { finish() })
                            PopupType.SHORTCUTS -> ShortcutsContent(onClose = { finish() })
                            PopupType.PROPERTIES -> PropertiesContent(onClose = { finish() })
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
            text = "Properties",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (targets.size == 1) {
            val file = targets.first()
            val fileType = remember(file) { getFileType(file) }
            val formatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()) }
            val formattedDate = remember(file.lastModified) { formatter.format(file.lastModified) }

            var calculatedSize by remember { mutableStateOf<Long?>(null) }
            var isCalculatingSize by remember { mutableStateOf(file.isDirectory) }
            
            var extraRes by remember { mutableStateOf<String?>(null) }
            var extraDur by remember { mutableStateOf<String?>(null) }
            var isLoadingExtra by remember { mutableStateOf(false) }

            LaunchedEffect(file) {
                if (file.isDirectory) {
                    withContext(Dispatchers.IO) {
                        val size = calculateSizeRecursively(context, file)
                        withContext(Dispatchers.Main) {
                            calculatedSize = size
                            isCalculatingSize = false
                        }
                    }
                } else {
                    calculatedSize = file.length
                }

                if (fileType == "Image") {
                    isLoadingExtra = true
                    withContext(Dispatchers.IO) {
                        val res = getImageResolution(context, file)
                        withContext(Dispatchers.Main) {
                            extraRes = res
                            isLoadingExtra = false
                        }
                    }
                } else if (fileType == "Video") {
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
                } else if (fileType == "Audio") {
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

            PropertyRow("Name:", file.name)
            PropertyRow("Type:", fileType)
            
            if (isCalculatingSize) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "Size:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(80.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Calculating...", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                PropertyRow("Size:", Formatter.formatFileSize(context, calculatedSize ?: 0L))
            }
            
            PropertyRow("Location:", file.absolutePath)
            PropertyRow("Modified:", formattedDate)

            if (isLoadingExtra) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Loading metadata...", style = MaterialTheme.typography.bodySmall)
                }
            } else if (extraRes != null || extraDur != null) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                if (extraRes != null) PropertyRow("Resolution:", extraRes!!)
                if (extraDur != null) PropertyRow("Duration:", extraDur!!)
            }
        } else {
            // Multiple files
            var totalSize by remember { mutableStateOf<Long?>(null) }
            var isCalculatingSize by remember { mutableStateOf(true) }

            LaunchedEffect(targets) {
                withContext(Dispatchers.IO) {
                    var size = 0L
                    for (target in targets) {
                        size += calculateSizeRecursively(context, target)
                    }
                    withContext(Dispatchers.Main) {
                        totalSize = size
                        isCalculatingSize = false
                    }
                }
            }

            val dirsCount = targets.count { it.isDirectory }
            val filesCount = targets.size - dirsCount

            PropertyRow("Items:", "${targets.size} selected")
            PropertyRow("Contains:", "$dirsCount Folders, $filesCount Files")

            if (isCalculatingSize) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "Total Size:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(80.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Calculating...", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                PropertyRow("Total Size:", Formatter.formatFileSize(context, totalSize ?: 0L))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = onClose,
                modifier = Modifier.focusRequester(focusRequester)
            ) {
                Text("Close")
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
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
    ) {
        Text(
            text = "Shortcuts Cheatsheet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

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

        Spacer(modifier = Modifier.height(16.dp))
        ShortcutCategory("Mouse")
        ShortcutItem("Right Click", "Context menu")
        ShortcutItem("Middle Click", "Open in new tab")
        ShortcutItem("Shift + Middle Click", "Open in new window")
        ShortcutItem("Drag", "Copy")
        ShortcutItem("Shift + Drag", "Move")

        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onClose) {
                Text("Close")
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
            text = "Create Archive",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = nameState,
            onValueChange = { nameState = it },
            label = { Text("Archive Name") },
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
                label = { Text("Compression Method") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expCompMethod) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expCompMethod,
                onDismissRequest = { expCompMethod = false }
            ) {
                CompressionMethod.values().forEach { method ->
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
                label = { Text("Compression Level") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expCompLevel) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expCompLevel,
                onDismissRequest = { expCompLevel = false }
            ) {
                CompressionLevel.values().forEach { level ->
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
                label = { Text("Encryption Method") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expEncMethod) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expEncMethod,
                onDismissRequest = { expEncMethod = false }
            ) {
                EncryptionMethod.values().forEach { method ->
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
                label = { Text("Password") },
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
            Text(text = "Delete source files after archiving", modifier = Modifier.padding(start = 8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                FileOperationsManager.onArchiveResult(ArchiveSettings("", CompressionMethod.STORE, CompressionLevel.NORMAL, EncryptionMethod.NONE, null, false, true))
                onClose()
            }) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onConfirm,
                enabled = nameState.text.isNotBlank() && (encryptionMethod == EncryptionMethod.NONE || password.isNotEmpty()),
                modifier = Modifier.focusRequester(focusRequester)
            ) {
                Text("Archive")
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
            text = "Extract Archive",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Choose extraction options for: $title",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = toSeparateFolder,
                onClick = { toSeparateFolder = true }
            )
            Text(
                text = "Extract to separate folder",
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = !toSeparateFolder,
                onClick = { toSeparateFolder = false }
            )
            Text(
                text = "Extract to current folder",
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
                text = "Delete source archive after extraction",
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { 
                FileOperationsManager.onExtractResult(ExtractSettings(false, false, true))
                onClose() 
            }) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { 
                    FileOperationsManager.onExtractResult(ExtractSettings(toSeparateFolder, deleteSource))
                },
                modifier = Modifier.focusRequester(focusRequester)
            ) {
                Text("Extract")
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
            text = "Encrypted Archive",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Please enter the password for: $title",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
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
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier.focusRequester(focusRequester)
            ) {
                Text("OK")
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
            onClose()
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
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm() })
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose) { Text("Cancel") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onConfirm,
                enabled = textState.text.isNotBlank(),
                modifier = Modifier.focusRequester(focusRequester)
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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "File Conflict", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "A file named \"$fileName\" already exists. What would you like to do?", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = rememberSelection, onCheckedChange = { rememberSelection = it })
            Text(text = "Remember selection for this session", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { FileOperationsManager.onCollisionChoice(CollisionResult.CANCEL, false) }) { Text("Cancel") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { FileOperationsManager.onCollisionChoice(CollisionResult.KEEP_BOTH, rememberSelection) }) { Text("Keep Both") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { FileOperationsManager.onCollisionChoice(CollisionResult.REPLACE, rememberSelection) },
                modifier = Modifier.focusRequester(focusRequester)
            ) { Text("Replace") }
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
        Text(text = "Confirm Delete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        val itemText = if (count == 1) "this item" else "$count items"
        Text(text = "Are you sure you want to delete $itemText?", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { FileOperationsManager.onDeleteChoice(DeleteResult.CANCEL) },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text("Cancel", maxLines = 1)
            }
            
            Button(
                onClick = { FileOperationsManager.onDeleteChoice(DeleteResult.RECYCLE) },
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.focusRequester(focusRequester)
            ) {
                Text("Recycle Bin", maxLines = 1, fontSize = 13.sp)
            }
            
            Button(
                onClick = { FileOperationsManager.onDeleteChoice(DeleteResult.PERMANENT) },
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text("Permanently", maxLines = 1, fontSize = 13.sp)
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
        Text(text = "Permanently Delete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        val itemText = if (count == 1) "this item" else "$count items"
        Text(text = "Are you sure you want to delete $itemText permanently? This action cannot be undone.", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { FileOperationsManager.onDeleteChoice(DeleteResult.CANCEL) }) { Text("No") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { FileOperationsManager.onDeleteChoice(DeleteResult.PERMANENT) },
                modifier = Modifier.focusRequester(focusRequester)
            ) { Text("Yes") }
        }
    }
}


@Composable
fun ProgressContent (onClose: () -> Unit) {
    val context = LocalContext.current
    val progress = FileOperationsManager.progress.floatValue
    val message = FileOperationsManager.statusMessage.value
    val isRunning = FileOperationsManager.isOperating.value
    val currentFileName = FileOperationsManager.currentFileName.value
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
        if (!isRunning) "Done"
        else if (timeRemainingMillis <= 0) "Calculating..."
        else {
            val seconds = timeRemainingMillis / 1000
            if (seconds > 60) "About ${seconds / 60} min remaining" else "About $seconds sec remaining"
        }
    }

    LaunchedEffect(isRunning) { 
        if (!isRunning) { 
            focusRequester.requestFocus()
            delay(1500)
            onClose() 
        } 
    }
    
    val titleText = if (isRunning) {
        if (message.startsWith("Deleting", ignoreCase = true)) "Deleting items..." 
        else if (message.startsWith("Extracting", ignoreCase = true)) "Extracting archive..."
        else if (message.startsWith("Compressing", ignoreCase = true)) "Creating archive..."
        else "Processing items..."
    } else "Operation Finished"

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = titleText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().height(18.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        DetailRow(label = "Name:", value = currentFileName)
        DetailRow(label = "Time remaining:", value = timeString)
        DetailRow(label = "Items remaining:", value = "$itemsRemaining ($sizeRemainingString)")
        DetailRow(label = "Speed:", value = "${Formatter.formatFileSize(context, speedBytesPerSec)}/s")
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!isRunning) {
                Button(
                    onClick = onClose,
                    modifier = Modifier.focusRequester(focusRequester)
                ) { Text("Close") }
            } else {
                TextButton(onClick = { NotificationHelper.start(context); onClose() }) { Text("Background") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { FileOperationsManager.cancel() }) { Text("Cancel") }
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
