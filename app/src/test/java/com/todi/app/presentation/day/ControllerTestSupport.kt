package com.todi.app.presentation.day

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle

fun TestScope.collectState(controller: TaskListController): Job {
    val job = launch { controller.uiState.collect {} }
    advanceUntilIdle()
    return job
}
