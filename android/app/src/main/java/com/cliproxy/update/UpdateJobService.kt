package com.cliproxy.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.cliproxy.MainActivity
import com.cliproxy.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class UpdateJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runningJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        runningJob = serviceScope.launch {
            val result = UpdateManager.checkForUpdate(applicationContext)
            if (result is UpdateCheckResult.Available) {
                showUpdateNotification(result.update)
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        runningJob?.cancel()
        runningJob = null
        return true
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun showUpdateNotification(update: AppUpdate) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.update_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_SHOW_UPDATE, true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_proxy_running)
            .setContentTitle(getString(R.string.update_notification_title))
            .setContentText(getString(R.string.update_notification_message, update.coreVersion))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val JOB_ID = 8318
        private const val CHANNEL_ID = "cliproxy_updates"
        private const val NOTIFICATION_ID = 8318
        private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            if (scheduler.getPendingJob(JOB_ID) != null) return

            val jobInfo = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, UpdateJobService::class.java)
            )
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(CHECK_INTERVAL_MS)
                .setPersisted(true)
                .build()
            scheduler.schedule(jobInfo)
        }
    }
}
