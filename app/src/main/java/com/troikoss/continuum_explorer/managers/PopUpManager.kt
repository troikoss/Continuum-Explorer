package com.troikoss.continuum_explorer.managers

import android.content.Context
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.R
import kotlinx.coroutines.CompletableDeferred
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod

enum class PopupType {
    PROGRESS,
    INPUT_TEXT,
    COLLISION,
    DELETE_CONFIRM,
    DELETE_PERMANENT_CONFIRM,
    PASSWORD_INPUT,
    EXTRACT_OPTIONS,
    ARCHIVE_OPTIONS,
    SHORTCUTS,
    PROPERTIES,
    MOVE_COPY_CHOICE
}

enum class CollisionResult {
    REPLACE,
    KEEP_BOTH,
    MERGE,
    CANCEL
}

enum class DeleteResult {
    RECYCLE,
    PERMANENT,
    CANCEL
}

enum class OperationType {
    RENAME,
    COMPRESS,
    EXTRACT,
    DELETE,
    NONE,
    COPY,
    TRASH,
    RESTORE
}

enum class MoveCopyResult {
    MOVE,
    COPY,
    CANCEL
}

data class ExtractSettings(
    val toSeparateFolder: Boolean,
    val deleteSource: Boolean,
    val isCancelled: Boolean = false
)

data class ArchiveSettings(
    val archiveName: String,
    val compressionMethod: CompressionMethod,
    val compressionLevel: CompressionLevel,
    val encryptionMethod: EncryptionMethod,
    val password: String?,
    val deleteSource: Boolean,
    val isCancelled: Boolean = false
)

/**
 * A global object to hold the state of the current file operation.
 * Both FileUtils (logic) and PopUpActivity (UI) will talk to this.
 */
object FileOperationsManager {
    var currentOperationType = mutableStateOf(OperationType.NONE)
    var currentFileName = mutableStateOf<String?>(null)
    var currentProcessedItems = mutableIntStateOf(0)

    // What kind of popup to show
    var popupType = mutableStateOf(PopupType.PROGRESS)

    // Is the window supposed to be open?
    var isOperating = mutableStateOf(false)

    // The current progress (0.0 to 1.0)
    var progress = mutableFloatStateOf(0f)

    // Detailed Stats
    var totalSize = mutableLongStateOf(0L)
    var processedSize = mutableLongStateOf(0L)
    var itemsTotal = mutableIntStateOf(0)
    var itemsProcessed = mutableIntStateOf(0)
    var currentSpeed = mutableLongStateOf(0L) // Bytes per second
    var timeRemaining = mutableLongStateOf(0L) // Milliseconds

    // Cancellation
    var isCancelled = mutableStateOf(false)

    // Input Text State
    var dialogTitle = mutableStateOf("")
    var confirmButtonText = mutableStateOf("")
    var initialInputText = mutableStateOf("")

    // This holds the function to run when the user clicks "Create" or "Rename"
    var onTextInputConfirm: ((String) -> Unit)? = null

    // Collision Resolution State
    var collisionFileName = mutableStateOf("")
    var collisionIsDirectory = mutableStateOf(false)
    var rememberedCollisionResult: CollisionResult? = null
    private var collisionDeferred: CompletableDeferred<CollisionResult>? = null

    // Move/Copy Choice State
    private var moveCopyDeferred: CompletableDeferred<MoveCopyResult>? = null

    // Delete Confirmation State
    var deleteItemCount = mutableIntStateOf(0)
    private var deleteDeferred: CompletableDeferred<DeleteResult>? = null

    // Password Input State
    private var passwordDeferred: CompletableDeferred<String?>? = null
    
    // Extraction Options State
    private var extractDeferred: CompletableDeferred<ExtractSettings>? = null

    // Archive Options State
    private var archiveDeferred: CompletableDeferred<ArchiveSettings>? = null

    // Properties State
    var propertiesTargets = mutableStateOf<List<UniversalFile>>(emptyList())

    // List of listeners to be notified on updates
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    fun getTitleText(context: Context): String {
        if (!isOperating.value && !isCancelled.value) return context.getString(R.string.op_finished)
        if (isCancelled.value) return context.getString(R.string.msg_cancelled)
        
        val fileName = currentFileName.value ?: ""
        val count = currentProcessedItems.intValue

        return when (currentOperationType.value) {
            OperationType.DELETE -> {
                if (count == 1) context.getString(R.string.op_deleting_an_item)
                else context.getString(R.string.op_deleting_items, count)
            }
            OperationType.EXTRACT -> {
                if (count == 1) context.getString(R.string.op_extracting_an_archive)
                else context.getString(R.string.op_extracting_archives, count)
            }
            OperationType.COMPRESS -> {
                if (count == 1) context.getString(R.string.op_compressing_an_item)
                else context.getString(R.string.op_compressing_items, count)
            }
            OperationType.RENAME -> context.getString(R.string.op_renaming, fileName)
            OperationType.COPY -> {
                if (count == 1) context.getString(R.string.op_copying)
                else context.getString(R.string.op_copying_items, count)
            }
            OperationType.TRASH -> {
                if (count == 1) context.getString(R.string.op_trashing)
                else context.getString(R.string.op_trashing_items, count)
            }
            OperationType.RESTORE -> {
                if (count == 1) context.getString(R.string.op_restoring)
                else context.getString(R.string.op_restoring_items, count)
            }
            OperationType.NONE -> ""
        }
    }


    fun start() {
        isOperating.value = true
        isCancelled.value = false // Reset cancellation
        progress.value = 0f
        popupType.value = PopupType.PROGRESS
        
        totalSize.longValue = 0L
        processedSize.longValue = 0L
        itemsTotal.intValue = 0
        itemsProcessed.intValue = 0
        currentSpeed.longValue = 0L
        timeRemaining.longValue = 0L
        currentFileName.value = ""
        currentProcessedItems.intValue = 0
        
        rememberedCollisionResult = null
        collisionDeferred = null
        moveCopyDeferred = null
        deleteDeferred = null
        passwordDeferred = null
        extractDeferred = null
        archiveDeferred = null
        
        notifyListeners()
    }

    fun openInput(
        title: String,
        initialText: String,
        buttonText: String,
        onConfirm: (String) -> Unit
    ) {
        dialogTitle.value = title
        initialInputText.value = initialText
        confirmButtonText.value = buttonText
        onTextInputConfirm = onConfirm
        popupType.value = PopupType.INPUT_TEXT
        isOperating.value = false
        notifyListeners()
    }

    suspend fun resolveCollision(fileName: String, isDirectory: Boolean = false): CollisionResult {
        if (rememberedCollisionResult != null) return rememberedCollisionResult!!
        
        collisionFileName.value = fileName
        collisionIsDirectory.value = isDirectory
        popupType.value = PopupType.COLLISION
        
        val deferred = CompletableDeferred<CollisionResult>()
        collisionDeferred = deferred
        notifyListeners()
        
        val result = deferred.await()
        popupType.value = PopupType.PROGRESS
        notifyListeners()
        return result
    }

    fun onCollisionChoice(result: CollisionResult, remember: Boolean) {
        if (remember) {
            rememberedCollisionResult = result
        }
        collisionDeferred?.complete(result)
        collisionDeferred = null
    }

    suspend fun requestMoveCopyChoice(): MoveCopyResult {
        popupType.value = PopupType.MOVE_COPY_CHOICE
        val deferred = CompletableDeferred<MoveCopyResult>()
        moveCopyDeferred = deferred
        notifyListeners()
        val result = deferred.await()
        popupType.value = PopupType.PROGRESS
        notifyListeners()
        return result
    }

    fun onMoveCopyChoice(result: MoveCopyResult) {
        moveCopyDeferred?.complete(result)
        moveCopyDeferred = null
    }

    suspend fun confirmDelete(count: Int, permanentOnly: Boolean = false): DeleteResult {
        deleteItemCount.intValue = count
        popupType.value = if (permanentOnly) PopupType.DELETE_PERMANENT_CONFIRM else PopupType.DELETE_CONFIRM
        
        val deferred = CompletableDeferred<DeleteResult>()
        deleteDeferred = deferred
        notifyListeners()
        
        val result = deferred.await()
        popupType.value = PopupType.PROGRESS
        notifyListeners()
        return result
    }

    fun onDeleteChoice(result: DeleteResult) {
        deleteDeferred?.complete(result)
        deleteDeferred = null
    }

    suspend fun requestPassword(title: String): String? {
        dialogTitle.value = title
        popupType.value = PopupType.PASSWORD_INPUT
        
        val deferred = CompletableDeferred<String?>()
        passwordDeferred = deferred
        notifyListeners()
        
        val result = deferred.await()
        popupType.value = PopupType.PROGRESS
        notifyListeners()
        return result
    }

    fun onPasswordResult(password: String?) {
        passwordDeferred?.complete(password)
        passwordDeferred = null
    }
    
    suspend fun requestExtractOptions(title: String): ExtractSettings {
        dialogTitle.value = title
        popupType.value = PopupType.EXTRACT_OPTIONS
        
        val deferred = CompletableDeferred<ExtractSettings>()
        extractDeferred = deferred
        notifyListeners()
        
        val result = deferred.await()
        popupType.value = PopupType.PROGRESS
        notifyListeners()
        return result
    }
    
    fun onExtractResult(settings: ExtractSettings) {
        extractDeferred?.complete(settings)
        extractDeferred = null
    }

    suspend fun requestArchiveOptions(defaultName: String): ArchiveSettings {
        initialInputText.value = defaultName
        popupType.value = PopupType.ARCHIVE_OPTIONS
        
        val deferred = CompletableDeferred<ArchiveSettings>()
        archiveDeferred = deferred
        notifyListeners()
        
        val result = deferred.await()
        popupType.value = PopupType.PROGRESS
        notifyListeners()
        return result
    }

    fun onArchiveResult(settings: ArchiveSettings) {
        archiveDeferred?.complete(settings)
        archiveDeferred = null
    }

    fun showShortcuts() {
        popupType.value = PopupType.SHORTCUTS
        isOperating.value = false
        notifyListeners()
    }

    fun showProperties(files: List<UniversalFile>) {
        propertiesTargets.value = files
        popupType.value = PopupType.PROPERTIES
        isOperating.value = false
        notifyListeners()
    }

    fun openRename(file: UniversalFile, context: Context, onConfirm: (String) -> Unit) {
        openInput(
            title = context.getString(R.string.menu_rename),
            initialText = file.name,
            buttonText = context.getString(R.string.menu_rename),
            onConfirm = onConfirm
        )
    }

    fun openCreateFolder(context: Context, onConfirm: (String) -> Unit) {
        openInput(
            title = context.getString(R.string.dialog_new_folder),
            initialText = context.getString(R.string.dialog_new_folder),
            buttonText = context.getString(R.string.create),
            onConfirm = onConfirm
        )
    }

    fun openCreateFile(context: Context, onConfirm: (String) -> Unit) {
        openInput(
            title = context.getString(R.string.dialog_new_file),
            initialText = "${context.getString(R.string.dialog_new_file)}.txt",
            buttonText = context.getString(R.string.create),
            onConfirm = onConfirm
        )
    }

    fun cancelSoft() {
        isCancelled.value = true
        notifyListeners()
    }

    fun cancel() {
        isCancelled.value = true
        // Also cancel any pending dialogs
        collisionDeferred?.takeIf { !it.isCompleted }?.complete(CollisionResult.CANCEL)
        moveCopyDeferred?.complete(MoveCopyResult.CANCEL)
        deleteDeferred?.complete(DeleteResult.CANCEL)
        passwordDeferred?.complete(null)
        extractDeferred?.complete(ExtractSettings(false, false, true))
        archiveDeferred?.complete(ArchiveSettings("", CompressionMethod.STORE, CompressionLevel.NORMAL, EncryptionMethod.NONE, null, false, true))
        notifyListeners()
    }

    fun update(current: Int, total: Int, operationType: OperationType = OperationType.NONE, fileName: String? = null) {
        progress.floatValue = if (total > 0) current.toFloat() / total.toFloat() else 0f
        currentOperationType.value = if (operationType != OperationType.NONE) operationType else currentOperationType.value
        currentFileName.value = fileName ?: currentFileName.value
        itemsProcessed.intValue = current
        notifyListeners()
    }

    fun updateDetailed(
        processedBytes: Long,
        totalBytes: Long,
        speed: Long,
        remainingMillis: Long,
        fileName: String
    ) {
        processedSize.longValue = processedBytes
        totalSize.longValue = totalBytes
        currentSpeed.longValue = speed
        timeRemaining.longValue = remainingMillis
        currentFileName.value = fileName
        
        if (totalBytes > 0) {
            progress.floatValue = processedBytes.toFloat() / totalBytes.toFloat()
        }
        notifyListeners()
    }

    fun finish() {
        isOperating.value = false
        if (!isCancelled.value) {
             progress.floatValue = 1f
             processedSize.longValue = totalSize.longValue
             itemsProcessed.intValue = itemsTotal.intValue
        }

        timeRemaining.longValue = 0
        currentSpeed.longValue = 0
        notifyListeners()
    }
}
