package com.todi.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.todi.app.di.ApplicationScope
import com.todi.app.domain.util.TodayProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootTimeReceiver : BroadcastReceiver() {
    @Inject lateinit var todayProvider: TodayProvider

    @Inject lateinit var midnightScheduler: MidnightWorkScheduler

    @Inject lateinit var globalReminderScheduler: GlobalReminderScheduler

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in HANDLED_ACTIONS) return
        todayProvider.refresh()
        midnightScheduler.schedule()

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                globalReminderScheduler.reschedule()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_DATE_CHANGED,
            )
    }
}
