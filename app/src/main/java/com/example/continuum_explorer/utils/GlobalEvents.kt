package com.example.continuum_explorer.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A global event bus to communicate between different windows/instances of the app.
 */
object GlobalEvents {
    private val _refreshEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshEvent = _refreshEvent.asSharedFlow()

    private val _configChangeEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val configChangeEvent = _configChangeEvent.asSharedFlow()

    /**
     * Triggers a refresh across all active file explorer windows for file contents.
     */
    fun triggerRefresh() {
        _refreshEvent.tryEmit(Unit)
    }

    /**
     * Triggers a configuration sync across all active file explorer windows (e.g. favorites changes).
     */
    fun triggerConfigUpdate() {
        _configChangeEvent.tryEmit(Unit)
    }
}
