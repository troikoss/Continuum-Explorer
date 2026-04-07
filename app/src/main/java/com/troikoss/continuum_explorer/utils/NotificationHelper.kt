package com.troikoss.continuum_explorer.utils

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
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.managers.FileOperationsManager
import com.troikoss.continuum_explorer.ui.activities.PopUpActivity

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
            val name = context.getString(R.string.settings_file_ops)
            val descriptionText = context.getString(R.string.notif_description)
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
                    .setContentTitle(context.getString(R.string.op_finished))
                    .setContentText(context.getString(R.string.msg_op_completed))
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
            timeRemainingMillis <= 0 -> context.getString(R.string.calculating)
            timeRemainingMillis < 60000 -> context.getString(R.string.op_sec_remaining, timeRemainingMillis / 1000)
            else -> context.getString(R.string.op_min_remaining, timeRemainingMillis / 60000)
        }

        val title = if (isCancelled) context.getString(R.string.msg_cancelled) else FileOperationsManager.getTitleText(context)
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
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.cancel), cancelPendingIntent)
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
        const val ACTION_CANCEL = "com.troikoss.continuum_explorer.ACTION_CANCEL_OPERATION"
    }
}
