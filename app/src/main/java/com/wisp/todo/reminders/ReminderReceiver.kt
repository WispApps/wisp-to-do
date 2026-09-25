package com.wisp.todo.reminders

import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wisp.todo.R
import com.wisp.todo.WispApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        val notification = NotificationCompat.Builder(context, "reminders")
            .setSmallIcon(R.drawable.ic_notification_wisp)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.wisp_original))
            .setContentIntent(PendingIntent.getActivity(context, id,
                Intent(context, com.wisp.todo.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setColor(ContextCompat.getColor(context, R.color.wisp_mint))
            .setContentTitle("Wisp To Do")
            .setContentText(intent.getStringExtra("title") ?: "Task reminder")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }
}

object ReminderScheduler {
    fun schedule(context: Context, taskId: String, title: String, at: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("id", taskId.hashCode()).putExtra("title", title)
        val pending = PendingIntent.getBroadcast(context, taskId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val manager = context.getSystemService(AlarmManager::class.java)
        if (android.os.Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }
    fun cancel(context: Context, taskId: String) {
        val pending = PendingIntent.getBroadcast(context, taskId.hashCode(), Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) context.getSystemService(AlarmManager::class.java).cancel(pending)
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? WispApp ?: return@launch
                val now = System.currentTimeMillis()
                app.db.dao().allTasks()
                    .filter { task -> !task.completed && task.reminderAt?.let { it > now } == true }
                    .forEach { task ->
                        ReminderScheduler.schedule(context, task.id, task.title, task.reminderAt!!)
                    }
            } catch (_: Exception) {
                // Boot-time alarm restoration is best-effort; the app remains usable if storage is unavailable.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
