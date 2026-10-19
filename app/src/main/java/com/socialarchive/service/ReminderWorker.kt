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

class ReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val postId = inputData.getLong("POST_ID", -1L)
        if (postId == -1L) return Result.failure()

        val repository = PostRepository(applicationContext)
        val post = repository.getPostById(postId) ?: return Result.success()

        sendNotification(post.title.ifEmpty { post.url }, post.platform.name, postId)
        return Result.success()
    }

    private fun sendNotification(title: String, platform: String, postId: Long) {
        val channelId = "post_reminders_channel"
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "İçerik Hatırlatıcıları",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Sosyal medya arşivindeki gönderileri hatırlatır."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_POST_ID", postId)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            postId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(com.socialarchive.R.drawable.ic_bell)
            .setContentTitle("Sosyal Arşiv Hatırlatması")
            .setContentText("[$platform] $title")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(postId.toInt(), notification)
    }
}
