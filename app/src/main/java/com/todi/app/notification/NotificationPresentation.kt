package com.todi.app.notification

import com.todi.app.domain.model.AppSettings
import com.todi.app.domain.model.LockScreenScope

data class NotificationPresentation(
    val showOnLockScreen: Boolean = true,
    val lockScreenScope: LockScreenScope = LockScreenScope.FULL,
    val colors: NotificationColors = NotificationColors.SYSTEM,
) {
    val detail: NotificationDetail
        get() =
            if (showOnLockScreen && lockScreenScope == LockScreenScope.TITLE_ONLY) {
                NotificationDetail.COUNT_ONLY
            } else {
                NotificationDetail.FULL
            }

    fun lockScreenContent(content: NotificationContent): NotificationContent? = if (showOnLockScreen) content else null

    companion object {
        fun from(settings: AppSettings) =
            NotificationPresentation(
                showOnLockScreen = settings.showOnLockScreen,
                lockScreenScope = settings.lockScreenScope,
                colors =
                    NotificationColors(
                        today = settings.notificationColorToday,
                        overdue = settings.notificationColorOverdue,
                        dateLess = settings.notificationColorDateLess,
                    ),
            )
    }
}
