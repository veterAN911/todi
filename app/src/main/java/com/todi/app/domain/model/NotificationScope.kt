package com.todi.app.domain.model

enum class NotificationScope {
    TODAY_ONLY,
    WITH_OVERDUE,
    WITH_OVERDUE_AND_GLOBAL,
    ;

    val includesOverdue: Boolean get() = this != TODAY_ONLY

    val includesDateLess: Boolean get() = this == WITH_OVERDUE_AND_GLOBAL
}
