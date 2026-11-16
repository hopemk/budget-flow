package com.budgetflow.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.budgetflow.app.domain.util.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : ReminderScheduler {

    override fun setBillRemindersEnabled(enabled: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, BillReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (!enabled) {
            alarmManager.cancel(pending)
            return
        }
        val nextNineAm = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            nextNineAm.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pending
        )
    }

    companion object {
        private const val REQUEST_CODE = 4100
    }
}
