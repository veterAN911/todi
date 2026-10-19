package com.todi.app.domain.usecase

import com.todi.app.domain.model.Task
import com.todi.app.domain.repository.TaskRepository
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

class CreateTaskUseCase
    @Inject
    constructor(
        private val repository: TaskRepository,
    ) {
        suspend operator fun invoke(
            title: String,
            date: LocalDate,
            time: LocalTime? = null,
            note: String? = null,
            isGlobal: Boolean = false,
        ): Task =
            repository.createTask(
                title = title.trim(),
                date = date,
                time = time,
                note = note?.trim()?.ifEmpty { null },
                isGlobal = isGlobal,
            )
    }
