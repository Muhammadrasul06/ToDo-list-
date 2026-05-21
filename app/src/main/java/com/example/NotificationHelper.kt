package com.example

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.data.TodoItem

object NotificationHelper {
    private const val TAG = "NotificationHelper"

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleAlarm(context: Context, item: TodoItem) {
        val reminderTime = item.reminderTime ?: return
        if (reminderTime <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TodoReminderReceiver::class.java).apply {
            putExtra(TodoReminderReceiver.EXTRA_TODO_ID, item.id)
            putExtra(TodoReminderReceiver.EXTRA_TODO_TITLE, item.title)
            putExtra(TodoReminderReceiver.EXTRA_TODO_NOTES, item.notes)
            putExtra(TodoReminderReceiver.EXTRA_TODO_CATEGORY, item.category)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            item.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminderTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled exact alarm at $reminderTime for ${item.title}")
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminderTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled inexact alarm (exact permission missing) for ${item.title}")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderTime,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled exact alarm (Pre-S) at $reminderTime for ${item.title}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    reminderTime,
                    pendingIntent
                )
                Log.e(TAG, "Failed standard exact alarm, scheduled basic fallback: ${e.localizedMessage}")
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun cancelAlarm(context: Context, item: TodoItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TodoReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            item.id.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Cancelled alarm for item: ${item.title}")
        }
    }
}
