package com.todi.app.notification

data class NotificationColors(
    val today: Int? = null,
    val overdue: Int? = null,
    val dateLess: Int? = null,
) {
    fun forSection(section: NotificationSection): Int? =
        when (section) {
            NotificationSection.TODAY -> today
            NotificationSection.OVERDUE -> overdue
            NotificationSection.DATE_LESS -> dateLess
        }

    companion object {
        val SYSTEM = NotificationColors()
    }
}
