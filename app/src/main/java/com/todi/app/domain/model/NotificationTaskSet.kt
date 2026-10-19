package com.todi.app.domain.model

data class NotificationTaskSet(
    val today: List<Task>,
    val overdue: List<Task> = emptyList(),
    val dateLess: List<Task> = emptyList(),
)
