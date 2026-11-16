package com.budgetflow.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.budgetflow.app.MainActivity
import com.budgetflow.app.R
import com.budgetflow.app.domain.repository.BillsGoalsRepository
import com.budgetflow.app.domain.repository.PreferencesRepository
import com.budgetflow.app.domain.util.TimeProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BillReminderReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReminderEntryPoint {
        fun preferencesRepository(): PreferencesRepository
        fun billsGoalsRepository(): BillsGoalsRepository
        fun timeProvider(): TimeProvider
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ReminderEntryPoint::class.java
                )
                val prefsRepo = entryPoint.preferencesRepository()
                val billsRepo = entryPoint.billsGoalsRepository()
                if (!prefsRepo.observeBillRemindersEnabled().first()) return@launch

                val today = entryPoint.timeProvider().today().dayOfMonth
                val dueBills = billsRepo.observeRecurringBills().first().filter { bill ->
                    !bill.isPaidThisMonth && (bill.dueDayOfMonth <= today || bill.dueDayOfMonth - today <= 2)
                }
                if (dueBills.isEmpty()) return@launch

                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notification_channel_bill_reminders),
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                    manager.createNotificationChannel(channel)
                }

                val openIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val summary = dueBills.joinToString(", ") { it.name }
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(context.getString(R.string.bill_reminder_title, dueBills.size))
                    .setContentText(summary)
                    .setContentIntent(openIntent)
                    .setAutoCancel(true)
                    .build()

                manager.notify(NOTIFICATION_ID, notification)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "budgetflow_bill_reminders"
        private const val NOTIFICATION_ID = 4101
    }
}
