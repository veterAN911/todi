package com.todi.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.todi.app.R
import com.todi.app.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskNotificationManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun ensureChannel() {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            createChannel(
                manager = manager,
                id = CHANNEL_ID,
                nameRes = R.string.notification_channel_name,
                descriptionRes = R.string.notification_channel_desc,
            )
            createChannel(
                manager = manager,
                id = REMINDER_CHANNEL_ID,
                nameRes = R.string.notification_reminder_channel_name,
                descriptionRes = R.string.notification_reminder_channel_desc,
            )

            if (manager.getNotificationChannel(LEGACY_CHANNEL_ID) != null) {
                manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
            }
        }

        private fun createChannel(
            manager: NotificationManager,
            id: String,
            @StringRes nameRes: Int,
            @StringRes descriptionRes: Int,
        ) {
            val existing = manager.getNotificationChannel(id)

            if (existing != null && existing.importance == NotificationManager.IMPORTANCE_NONE) return
            val channel =
                NotificationChannel(
                    id,
                    context.getString(nameRes),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(descriptionRes)

                    enableVibration(false)

                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }
            manager.createNotificationChannel(channel)
        }

        fun canPost(): Boolean {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            return true
        }

        fun update(
            content: NotificationContent,
            presentation: NotificationPresentation,
        ) {
            if (!canPost()) return

            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val builder =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_tasks)
                    .setContentIntent(openAppIntent(openEditor = false))
                    .addAction(
                        R.drawable.ic_notification_tasks,
                        context.getString(R.string.notification_action_add),
                        openAppIntent(openEditor = true),
                    ).addAction(
                        R.drawable.ic_notification_tasks,
                        context.getString(R.string.notification_action_open),
                        openAppIntent(openEditor = false),
                    ).setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(false)

            applyContent(builder, content, presentation)
            applyLockScreen(builder, presentation)

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }

        fun updateReminder(
            content: NotificationContent,
            timeoutMillis: Long,
            presentation: NotificationPresentation,
        ) {
            if (!canPost()) return

            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val builder =
                NotificationCompat
                    .Builder(context, REMINDER_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_tasks)
                    .setContentIntent(openAppIntent(openEditor = false))
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setTimeoutAfter(timeoutMillis)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)

            applyContent(builder, content, presentation)
            applyLockScreen(builder, presentation)

            NotificationManagerCompat.from(context).notify(REMINDER_NOTIFICATION_ID, builder.build())
        }

        fun cancel() {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }

        fun cancelReminder() {
            NotificationManagerCompat.from(context).cancel(REMINDER_NOTIFICATION_ID)
        }

        fun localizedOverdueHeader(): String = context.getString(R.string.notification_overdue_header) + ":"

        fun localizedDateLessHeader(): String = context.getString(R.string.notification_global_header) + ":"

        fun titleFor(content: NotificationContent): String {
            val countText = context.resources.getQuantityString(R.plurals.task_count, content.count, content.count)
            return if (content.datedCount > 0) {
                context.getString(R.string.notification_title, countText)
            } else {
                context.getString(R.string.notification_title_global, countText)
            }
        }

        private fun applyContent(
            builder: NotificationCompat.Builder,
            content: NotificationContent,
            presentation: NotificationPresentation,
        ) {
            val colors = presentation.colors
            val title = titleFor(content)
            builder.setContentTitle(title)

            builder.setContentText(content.preview.joinToString(PREVIEW_SEPARATOR) { it.text }.ifEmpty { null })
            builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())

            builder.setCustomContentView(contentViews(content, colors, content.preview, moreCount = 0))
            builder.setCustomBigContentView(
                contentViews(content, colors, content.lines, moreCount = content.moreCount),
            )
            builder.setColor(accentColor(content, colors))
        }

        internal fun contentViews(
            content: NotificationContent,
            colors: NotificationColors,
            lines: List<NotificationLine>,
            moreCount: Int,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.notification_content)
            views.setTextViewText(R.id.notification_title, titleFor(content))
            LINE_IDS.forEachIndexed { index, lineId ->
                val line = lines.getOrNull(index)
                if (line == null) {
                    views.setViewVisibility(lineId, View.GONE)
                    return@forEachIndexed
                }
                views.setViewVisibility(lineId, View.VISIBLE)
                views.setTextViewText(lineId, line.text)
                views.setTextViewTextSize(
                    lineId,
                    TypedValue.COMPLEX_UNIT_SP,
                    if (line.isHeader) HEADER_TEXT_SIZE_SP else LINE_TEXT_SIZE_SP,
                )
                views.setTextColor(lineId, lineColor(line, colors))
            }
            if (moreCount > 0) {
                views.setViewVisibility(R.id.notification_more, View.VISIBLE)
                views.setTextViewText(
                    R.id.notification_more,
                    context.getString(R.string.notification_more, moreCount),
                )
            } else {
                views.setViewVisibility(R.id.notification_more, View.GONE)
            }
            return views
        }

        private fun lineColor(
            line: NotificationLine,
            colors: NotificationColors,
        ): Int =
            colors.forSection(line.section) ?: ContextCompat.getColor(
                context,
                if (line.isHeader) R.color.notification_text_secondary else R.color.notification_text_primary,
            )

        private fun applyLockScreen(
            builder: NotificationCompat.Builder,
            presentation: NotificationPresentation,
        ) {
            builder.setVisibility(
                if (presentation.showOnLockScreen) {
                    NotificationCompat.VISIBILITY_PUBLIC
                } else {
                    NotificationCompat.VISIBILITY_SECRET
                },
            )
        }

        private fun accentColor(
            content: NotificationContent,
            colors: NotificationColors,
        ): Int {
            val section = content.lines.firstOrNull()?.section ?: content.preview.firstOrNull()?.section
            return section?.let(colors::forSection) ?: ContextCompat.getColor(context, R.color.todi_accent)
        }

        private fun openAppIntent(openEditor: Boolean): PendingIntent {
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (openEditor) putExtra(EXTRA_OPEN_EDITOR, true)
                }
            return PendingIntent.getActivity(
                context,
                if (openEditor) EDITOR_REQUEST_CODE else APP_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        companion object {
            const val CHANNEL_ID = "today_tasks_v2"

            const val REMINDER_CHANNEL_ID = "global_reminder_v1"

            private const val LEGACY_CHANNEL_ID = "today_tasks"
            const val NOTIFICATION_ID = 1001
            const val REMINDER_NOTIFICATION_ID = 1002
            const val EXTRA_OPEN_EDITOR = "com.todi.app.extra.OPEN_EDITOR"
            private const val PREVIEW_SEPARATOR = " · "
            private const val APP_REQUEST_CODE = 10
            private const val EDITOR_REQUEST_CODE = 11

            internal val LINE_IDS =
                intArrayOf(
                    R.id.notification_line_1,
                    R.id.notification_line_2,
                    R.id.notification_line_3,
                    R.id.notification_line_4,
                    R.id.notification_line_5,
                    R.id.notification_line_6,
                )
            private const val LINE_TEXT_SIZE_SP = 14f
            private const val HEADER_TEXT_SIZE_SP = 12f
        }
    }
