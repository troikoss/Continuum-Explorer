package com.troikoss.continuum_explorer.ui.activities

import android.app.Activity.FULLSCREEN_MODE_REQUEST_ENTER
import android.app.Activity.FULLSCREEN_MODE_REQUEST_EXIT
import android.os.Build
import android.os.OutcomeReceiver
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

open class FullscreenActivity: ComponentActivity() {

    var isFullscreen by mutableStateOf(false)
        protected set

    fun toggleFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && isInMultiWindowMode) {
            // Freeform window: need to expand the window first, then hide bars
            val request = if (isFullscreen)
                FULLSCREEN_MODE_REQUEST_EXIT
            else
                FULLSCREEN_MODE_REQUEST_ENTER

            requestFullscreenMode(request, object : OutcomeReceiver<Void, Throwable> {
                override fun onResult(result: Void?) {
                    isFullscreen = !isFullscreen
                    updateSystemBars()
                }
                override fun onError(error: Throwable) {
                    // Still try to hide bars as fallback
                    isFullscreen = !isFullscreen
                    updateSystemBars()
                }
            })
        } else {
            // Maximized or older API: window already covers screen, just toggle bars
            isFullscreen = !isFullscreen
            updateSystemBars()
        }
    }

    private fun updateSystemBars() {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullscreen) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}