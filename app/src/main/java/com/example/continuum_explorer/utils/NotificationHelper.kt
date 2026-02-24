package com.example.continuum_explorer.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.continuum_explorer.PopUpActivity

object NotificationHelper {
    private const val CHANNEL_ID = "file_explorer_progress"
    private const val NOTIFICATION_ID = 1001
    private var isRegistered = false
    private var appContext: Context? = null

    // Store the listener so we can remove it later
    private val updateListener: () -> Unit = {
        appContext?.let { context ->
            showProgressNotification(context)
        }
    }

    fun start(context: Context) {
        appContext = context.applicationContext
        createNotificationChannel(context)
        
        if (!isRegistered) {
            FileOperationsManager.addListener(updateListener)
            isRegistered = true
        }
        showProgressNotification(context)
    }

    fun stop() {
        if (isRegistered) {
            FileOperationsManager.removeListener(updateListener)
            isRegistered = false
        }
        appContext?.let {
            NotificationManagerCompat.from(it).cancel(NOTIFICATION_ID)
        }
        appContext = null
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "File Operations"
            val descriptionText = "Progress of file copy/move operations"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showProgressNotification(context: Context) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val isRunning = FileOperationsManager.isOperating.value
        val isCancelled = FileOperationsManager.isCancelled.value
        val progress = FileOperationsManager.progress.floatValue
        val message = FileOperationsManager.statusMessage.value
        val fileName = FileOperationsManager.currentFileName.value
        
        // Detailed Stats
        val speedBytesPerSec = FileOperationsManager.currentSpeed.longValue
        val timeRemainingMillis = FileOperationsManager.timeRemaining.longValue

        // Create Intent to open PopUpActivity
        val intent = Intent(context, PopUpActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Handle Finished State
        if (!isRunning) {
            if (isCancelled) {
                // If cancelled, remove the notification immediately
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            } else {
                // If finished successfully, show success message
                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("Operation Finished")
                    .setContentText("File operation completed.")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)

                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
            }

            // Unregister listener
            if (isRegistered) {
                FileOperationsManager.removeListener(updateListener)
                isRegistered = false
            }
            return
        }

        // Handle Ongoing State
        // Create Intent for Cancel Action
        val cancelIntent = Intent(context, NotificationCancelReceiver::class.java).apply {
            action = NotificationCancelReceiver.ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Format stats
        val speedString = "${Formatter.formatFileSize(context, speedBytesPerSec)}/s"
        val timeString = when {
            timeRemainingMillis <= 0 -> "Calculating..."
            timeRemainingMillis < 60000 -> "${timeRemainingMillis / 1000}s left"
            else -> "${timeRemainingMillis / 60000}m left"
        }

        val title = if (isCancelled) "Cancelling..." else message
        val contentText = "$speedString • $timeString"
        val progressInt = (progress * 100).toInt()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setSubText(contentText) // Shows speed in header as well
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$contentText\n$fileName")) // Detailed view
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, progressInt, false)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            
        // Only add cancel button if not already cancelling
        if (!isCancelled) {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }
}

class NotificationCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CANCEL) {
            FileOperationsManager.cancel()
        }
    }
    
    companion object {
        const val ACTION_CANCEL = "com.example.continuum_explorer.ACTION_CANCEL_OPERATION"
    }
}
