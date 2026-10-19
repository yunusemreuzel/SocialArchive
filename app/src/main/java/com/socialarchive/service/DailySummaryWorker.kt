package com.socialarchive.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.socialarchive.data.PostRepository
import com.socialarchive.ui.main.MainActivity

class DailySummaryWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "daily_summary_work"
        const val CHANNEL_ID = "daily_summary_channel"
    }

    override suspend fun doWork(): Result {
        val db = com.socialarchive.data.db.PostDatabase.getDatabase(applicationContext)
        val actualUnread = db.postDao().getUnreadCountSync()
        if (actualUnread > 0) {
            sendSummaryNotification(actualUnread)
        }
        return Result.success()
    }

    private fun sendSummaryNotification(unreadCount: Int) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Günlük Arşiv Özeti",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Her gün okunmamış içerik sayısını hatırlatır." }
            nm.createNotificationChannel(ch)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = when {
            unreadCount == 1 -> "Arşivinde 1 okunmamış içerik var! 📚"
            unreadCount < 10 -> "Arşivinde $unreadCount okunmamış içerik var! 📚"
            else -> "Arşivinde $unreadCount okunmamış içerik birikti! Okuma zamanı 🚀"
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(com.socialarchive.R.drawable.ic_bell)
            .setContentTitle("SocialArchive Günlük Özet")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(9999, notification)
    }
}
