package com.todi.app.presentation.editor

import com.todi.app.domain.FakeTodayProvider
import com.todi.app.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class TaskEditorViewModelTest {
    private val today = LocalDate.of(2026, 8, 30)

    private fun task(
        id: Long = 1L,
        isGlobal: Boolean = false,
        time: LocalTime? = null,
    ) = Task(
        id = id,
        title = "Позвонить в сервис",
        date = LocalDate.of(2026, 8, 20),
        time = time,
        note = "до 18:00",
        completed = false,
        createdAt = Instant.parse("2026-08-20T08:00:00Z"),
        updatedAt = Instant.parse("2026-08-20T08:00:00Z"),
        syncId = "sync-$id",
        isGlobal = isGlobal,
    )

    @Test
    fun openForCreate_fromTheGlobalSection_opensAsDateLess() {
        val viewModel = TaskEditorViewModel(FakeTodayProvider(today))

        viewModel.openForCreate(today, isGlobal = true)

        val state = viewModel.state.value
        assertTrue(state.visible)
        assertTrue(state.isGlobal)
        assertNull(state.time)
        assertFalse("новое дело не редактирует существующее", state.isEdit)
    }

    @Test
    fun setGlobal_true_dropsTheTimeForGood() {
        val viewModel = TaskEditorViewModel(FakeTodayProvider(today))
        viewModel.openForCreate(today)
        viewModel.setTime(LocalTime.of(18, 30))

        viewModel.setGlobal(true)

        assertTrue(viewModel.state.value.isGlobal)
        assertNull(viewModel.state.value.time)

        viewModel.setGlobal(false)
        assertNull("выключение обратно не возвращает стёртое время", viewModel.state.value.time)
    }

    @Test
    fun setGlobal_false_keepsTheTitleAndTheDate() {
        val viewModel = TaskEditorViewModel(FakeTodayProvider(today))
        viewModel.openForCreate(today)
        viewModel.setTitle("Дело")
        viewModel.setGlobal(true)

        viewModel.setGlobal(false)

        val state = viewModel.state.value
        assertEquals("Дело", state.title)
        assertEquals(today, state.date)
        assertFalse(state.isGlobal)
    }

    @Test
    fun openForEdit_readsTheDateLessFlagFromTheTask() {
        val viewModel = TaskEditorViewModel(FakeTodayProvider(today))

        viewModel.openForEdit(task(isGlobal = true))

        val state = viewModel.state.value
        assertTrue(state.isGlobal)
        assertEquals(1L, state.editingTaskId)
        assertEquals("Позвонить в сервис", state.title)
        assertEquals("до 18:00", state.note)
    }

    @Test
    fun openForEdit_keepsTheTimeOfADatedTask() {
        val viewModel = TaskEditorViewModel(FakeTodayProvider(today))

        viewModel.openForEdit(task(time = LocalTime.of(9, 15)))

        assertFalse(viewModel.state.value.isGlobal)
        assertEquals(LocalTime.of(9, 15), viewModel.state.value.time)
    }
}
