package com.todi.app.domain.usecase

import com.todi.app.domain.model.Task
import com.todi.app.domain.util.ParsedTask
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class SaveVoiceDraftsUseCase
    @Inject
    constructor(
        private val createTask: CreateTaskUseCase,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(
            drafts: List<ParsedTask>,
            date: LocalDate?,
        ): List<Task> =
            drafts.map { draft ->
                createTask(
                    title = draft.title,
                    date = date ?: LocalDate.now(clock),
                    time = draft.time,
                    isGlobal = date == null,
                )
            }
    }
