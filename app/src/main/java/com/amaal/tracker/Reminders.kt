package com.amaal.tracker

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    fun parseTimes(value: String): List<LocalTime> = try {
        value.split(',').map {
            val time = it.trim()
            require(time.matches(Regex("([01][0-9]|2[0-3]):[0-5][0-9]")))
            LocalTime.parse(time)
        }
    } catch (_: Exception) { emptyList() }
    private fun pending(context: Context, index: Int): PendingIntent = PendingIntent.getBroadcast(context, index, Intent(context, ReminderReceiver::class.java).putExtra("index", index), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun schedule(context: Context) {
        val prefs = context.getSharedPreferences("amaal", 0)
        if (!prefs.getBoolean("reminders", false)) return
        val prayers = parseTimes(prefs.getString("prayerTimes", "05:00,13:00,16:00,18:00,20:00")!!)
        val nudge = parseTimes(prefs.getString("nudgeTime", "21:00")!!)
        if (prayers.size != 5 || nudge.size != 1) return
        val manager = context.getSystemService(AlarmManager::class.java)
        (prayers + nudge).forEachIndexed { index, time ->
            val now = ZonedDateTime.now()
            var next = now.toLocalDate().atTime(time).atZone(now.zone)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val alarm = pending(context, index)
            if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) {
                try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), alarm) }
                catch (_: SecurityException) { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), alarm) }
            } else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), alarm)
        }
        val work = PeriodicWorkRequestBuilder<ReminderWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("amaal-reschedule", ExistingPeriodicWorkPolicy.KEEP, work)
    }
    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        (0..5).forEach { manager.cancel(pending(context, it)) }
        WorkManager.getInstance(context).cancelUniqueWork("amaal-reschedule")
    }
    fun notify(context: Context, index: Int) {
        val prefs = context.getSharedPreferences("amaal", 0)
        if (!prefs.getBoolean("reminders", false)) return
        if (index == 5 && prefs.getString("cacheDate", "") == LocalDate.now().toString() && prefs.getInt("remaining", 1) == 0) return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("amaal-reminders", "Prayer & daily reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val title = if (index in 0..4) "${listOf("Fajr", "Zuhr", "Asr", "Maghrib", "Isha")[index]} reminder" else "A moment for your intentions"
        val intent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "amaal-reminders").setSmallIcon(com.amaal.tracker.R.drawable.ic_amaal)
            .setContentTitle(title).setContentText(if (index == 5) "Reflect on your day and record your remaining amals." else "Your scheduled prayer reminder. Pause, pray, reconnect.")
            .setContentIntent(intent).setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(index, notification)
    }
}
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { ReminderScheduler.notify(context, intent.getIntExtra("index", 5)); ReminderScheduler.schedule(context) }
}
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) ReminderScheduler.schedule(context)
    }
}
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result { ReminderScheduler.schedule(applicationContext); return Result.success() }
}
