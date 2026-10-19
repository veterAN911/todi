package com.todi.app.domain

import com.todi.app.domain.usecase.CarryOverIncompleteTasksUseCase
import com.todi.app.domain.usecase.CompleteTaskUseCase
import com.todi.app.domain.usecase.CreateTaskUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CarryOverTest {
    private val repository = FakeTaskRepository()
    private val todayProvider = FakeTodayProvider(LocalDate.of(2026, 8, 30))
    private val carryOver = CarryOverIncompleteTasksUseCase(repository, todayProvider)
    private val createTask = CreateTaskUseCase(repository)
    private val completeTask = CompleteTaskUseCase(repository)

    @Test
    fun carryOver_movesIncompletePastTasksToToday_andRemembersOrigin() =
        runTest {
            val yesterdayTask = createTask("Вчерашнее", LocalDate.of(2026, 8, 29), null, null)
            val olderTask = createTask("Старое", LocalDate.of(2026, 8, 20), null, null)
            val todayTask = createTask("Сегодняшнее", LocalDate.of(2026, 8, 30), null, null)

            val moved = carryOver()

            assertEquals(2, moved)
            val movedTask = repository.getTask(yesterdayTask.id)!!
            assertEquals(LocalDate.of(2026, 8, 30), movedTask.date)
            assertEquals(LocalDate.of(2026, 8, 29), movedTask.carriedFrom)
            assertEquals(LocalDate.of(2026, 8, 20), repository.getTask(olderTask.id)!!.carriedFrom)

            assertEquals(LocalDate.of(2026, 8, 30), repository.getTask(todayTask.id)!!.date)
            assertNull(repository.getTask(todayTask.id)!!.carriedFrom)
        }

    @Test
    fun carryOver_twice_keepsTheDateItFirstSlippedFrom() =
        runTest {
            val task = createTask("Дело", LocalDate.of(2026, 8, 29), null, null)

            carryOver()
            todayProvider.setDate(LocalDate.of(2026, 8, 31))
            carryOver()

            val carried = repository.getTask(task.id)!!
            assertEquals(LocalDate.of(2026, 8, 31), carried.date)
            assertEquals(LocalDate.of(2026, 8, 29), carried.carriedFrom)
        }

    @Test
    fun carryOver_neverTouchesTheCreationDate() =
        runTest {
            val task = createTask("Дело", LocalDate.of(2026, 8, 20), null, null)
            val createdAt = repository.getTask(task.id)!!.createdAt

            carryOver()
            todayProvider.setDate(LocalDate.of(2026, 8, 31))
            carryOver()

            val carried = repository.getTask(task.id)!!
            assertEquals(createdAt, carried.createdAt)
            assertEquals(LocalDate.of(2026, 8, 20), carried.carriedFrom)
        }

    @Test
    fun carryOver_leavesCompletedPastTasksInPlace() =
        runTest {
            val doneTask = createTask("Готово", LocalDate.of(2026, 8, 29), null, null)
            completeTask(doneTask.id, completed = true)

            val moved = carryOver()

            assertEquals(0, moved)
            val task = repository.getTask(doneTask.id)!!
            assertEquals(LocalDate.of(2026, 8, 29), task.date)
            assertNull(task.carriedFrom)
            assertTrue(task.completed)
        }

    @Test
    fun carryOver_isIdempotent() =
        runTest {
            createTask("Дело", LocalDate.of(2026, 8, 29), null, null)

            assertEquals(1, carryOver())
            assertEquals(0, carryOver())
        }

    @Test
    fun carryOver_neverTouchesGlobalTasks() =
        runTest {
            val global = createTask("Позвонить в сервис", LocalDate.of(2026, 8, 20), null, null, isGlobal = true)

            val moved = carryOver()

            assertEquals(0, moved)
            val task = repository.getTask(global.id)!!
            assertEquals(LocalDate.of(2026, 8, 20), task.date)
            assertNull(task.carriedFrom)
        }
}
