package com.wisp.todo

import android.app.*
import android.content.Context
import com.wisp.todo.data.*

class WispApp : Application() {
    val db by lazy { WispDatabase.create(this) }
    val repository by lazy { TaskRepository(db.dao()) }
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel("reminders", getString(com.wisp.todo.R.string.notification_channel), NotificationManager.IMPORTANCE_HIGH)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
