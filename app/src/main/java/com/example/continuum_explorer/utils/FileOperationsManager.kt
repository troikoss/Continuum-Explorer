package com.example.continuum_explorer.utils

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import com.example.continuum_explorer.model.UniversalFile
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
    ARCHIVE_OPTIONS
}

enum class CollisionResult {
    REPLACE,
    KEEP_BOTH,
    CANCEL
}

enum class DeleteResult {
    RECYCLE,
    PERMANENT,
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
    // What kind of popup to show
    var popupType = mutableStateOf(PopupType.PROGRESS)

    // Is the window supposed to be open?
    var isOperating = mutableStateOf(false)

    // The current progress (0.0 to 1.0)
    var progress = mutableFloatStateOf(0f)

    // The text message (e.g. "Pasting file 1 of 5")
    var statusMessage = mutableStateOf("Preparing...")

    // Detailed Stats
    var totalSize = mutableLongStateOf(0L)
    var processedSize = mutableLongStateOf(0L)
    var itemsTotal = mutableIntStateOf(0)
    var itemsProcessed = mutableIntStateOf(0)
    var currentSpeed = mutableLongStateOf(0L) // Bytes per second
    var timeRemaining = mutableLongStateOf(0L) // Milliseconds
    var currentFileName = mutableStateOf("")

    // Cancellation
    var isCancelled = mutableStateOf(false)

    // Input Text State
    var dialogTitle = mutableStateOf("Rename")
    var confirmButtonText = mutableStateOf("Rename")
    var initialInputText = mutableStateOf("")

    // This holds the function to run when the user clicks "Create" or "Rename"
    var onTextInputConfirm: ((String) -> Unit)? = null

    // Collision Resolution State
    var collisionFileName = mutableStateOf("")
    var rememberedCollisionResult: CollisionResult? = null
    private var collisionDeferred: CompletableDeferred<CollisionResult>? = null

    // Delete Confirmation State
    var deleteItemCount = mutableIntStateOf(0)
    private var deleteDeferred: CompletableDeferred<DeleteResult>? = null

    // Password Input State
    private var passwordDeferred: CompletableDeferred<String?>? = null
    
    // Extraction Options State
    private var extractDeferred: CompletableDeferred<ExtractSettings>? = null

    // Archive Options State
    private var archiveDeferred: CompletableDeferred<ArchiveSettings>? = null

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

    fun start() {
        isOperating.value = true
        isCancelled.value = false // Reset cancellation
        progress.value = 0f
        statusMessage.value = "Starting..."
        popupType.value = PopupType.PROGRESS
        
        totalSize.longValue = 0L
        processedSize.longValue = 0L
        itemsTotal.intValue = 0
        itemsProcessed.intValue = 0
        currentSpeed.longValue = 0L
        timeRemaining.longValue = 0L
        currentFileName.value = ""
        
        rememberedCollisionResult = null
        collisionDeferred = null
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

    suspend fun resolveCollision(fileName: String): CollisionResult {
        if (rememberedCollisionResult != null) return rememberedCollisionResult!!
        
        collisionFileName.value = fileName
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

    fun openRename(file: UniversalFile, onConfirm: (String) -> Unit) {
        openInput(
            title = "Rename",
            initialText = file.name,
            buttonText = "Rename",
            onConfirm = onConfirm
        )
    }

    fun openCreateFolder(onConfirm: (String) -> Unit) {
        openInput(
            title = "New Folder",
            initialText = "New Folder",
            buttonText = "Create",
            onConfirm = onConfirm
        )
    }

    fun openCreateFile(onConfirm: (String) -> Unit) {
        openInput(
            title = "New Text Document",
            initialText = "New Text Document.txt",
            buttonText = "Create",
            onConfirm = onConfirm
        )
    }

    fun cancel() {
        isCancelled.value = true
        statusMessage.value = "Cancelling..."
        // Also cancel any pending dialogs
        collisionDeferred?.complete(CollisionResult.CANCEL)
        deleteDeferred?.complete(DeleteResult.CANCEL)
        passwordDeferred?.complete(null)
        extractDeferred?.complete(ExtractSettings(false, false, true))
        archiveDeferred?.complete(ArchiveSettings("", CompressionMethod.STORE, CompressionLevel.NORMAL, EncryptionMethod.NONE, null, false, true))
        notifyListeners()
    }

    fun update(current: Int, total: Int, message: String) {
        progress.floatValue = if (total > 0) current.toFloat() / total.toFloat() else 0f
        statusMessage.value = message
        itemsProcessed.intValue = current
        itemsTotal.intValue = total
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
             statusMessage.value = "Done"
             processedSize.longValue = totalSize.longValue
             itemsProcessed.intValue = itemsTotal.intValue
        } else {
             statusMessage.value = "Cancelled"
        }
        
        timeRemaining.longValue = 0
        currentSpeed.longValue = 0
        notifyListeners()
    }
}
