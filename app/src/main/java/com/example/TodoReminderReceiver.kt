package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.TodoDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TodoReminderReceiver : BroadcastReceiver() {
    companion object {
        const val CHANNEL_ID = "todo_reminders_channel"
        const val EXTRA_TODO_ID = "extra_todo_id"
        const val EXTRA_TODO_TITLE = "extra_todo_title"
        const val EXTRA_TODO_NOTES = "extra_todo_notes"
        const val EXTRA_TODO_CATEGORY = "extra_todo_category"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getStringExtra(EXTRA_TODO_ID)
        val todoTitle = intent.getStringExtra(EXTRA_TODO_TITLE) ?: "Task Reminder"
        val todoNotes = intent.getStringExtra(EXTRA_TODO_NOTES) ?: "You have a task requiring attention!"
        val todoCategory = intent.getStringExtra(EXTRA_TODO_CATEGORY) ?: "General"

        Log.d("TodoReminderReceiver", "Alarm received for ID: $todoId, Title: $todoTitle")

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Re-schedule all alarms on boot!
            Log.d("TodoReminderReceiver", "Device reboot detected. Rescheduling reminders...")
            rescheduleAlarms(context)
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create the Notification Channel if Android Oreo or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "To-Do List Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channels for task alarms requiring immediate attention"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Action when clicking the Notification opens the App
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            todoId?.hashCode() ?: 0,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notification builder with premium color visual style
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // System icon is safer to ensure icon is always found during compile/run
            .setContentTitle(todoTitle)
            .setContentText("[$todoCategory] $todoNotes")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVibrate(longArrayOf(0, 500, 200, 500))

        notificationManager.notify(todoId?.hashCode() ?: System.currentTimeMillis().toInt(), builder.build())
    }

    private fun rescheduleAlarms(context: Context) {
        val db = TodoDatabase.getDatabase(context)
        CoroutineScope(Dispatchers.IO).launch {
            val items = db.todoDao().getAllRawItems()
            val now = System.currentTimeMillis()
            for (item in items) {
                if (!item.isCompleted && !item.isDeleted && item.reminderTime != null && item.reminderTime > now) {
                    // This is a future active reminder, re-schedule it!
                    NotificationHelper.scheduleAlarm(context, item)
                }
            }
        }
    }
}
