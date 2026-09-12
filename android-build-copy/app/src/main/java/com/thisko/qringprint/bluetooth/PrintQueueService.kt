package com.thisko.qringprint.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

class PrintQueueService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "打印队列", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("正在打印")
                .setContentText("打印任务进行中，请保持打印机开启")
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("正在打印")
                .setContentText("打印任务进行中，请保持打印机开启")
                .setOngoing(true)
                .build()
        }
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "print_queue"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PrintQueueService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PrintQueueService::class.java))
        }
    }
}
