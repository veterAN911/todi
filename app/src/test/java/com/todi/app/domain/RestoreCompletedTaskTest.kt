package com.todi.app.domain

import com.todi.app.domain.usecase.CompleteTaskUseCase
import com.todi.app.domain.usecase.CreateTaskUseCase
import com.todi.app.domain.usecase.DeleteTaskUseCase
import com.todi.app.domain.usecase.RestoreCompletedTaskUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class RestoreCompletedTaskTest {
    private val repository = FakeTaskRepository()
    private val createTask = CreateTaskUseCase(repository)
    private val completeTask = CompleteTaskUseCase(repository)
    private val restore = RestoreCompletedTaskUseCase(repository)
    private val deleteTask = DeleteTaskUseCase(repository)

    private suspend fun finish(
        title: String,
        isGlobal: Boolean = false,
    ): Long {
        val task = createTask(title, PLANNED_FOR, null, null, isGlobal)
        completeTask(task.id, completed = true)
        return task.id
    }

    @Test
    fun restoreToAnotherDay_takesItOutOfTheArchiveAndPutsItThere() =
        runTest {
            val id = finish("Купить хлеб")
            val target = LocalDate.of(2026, 9, 2)

            restore(id, target)

            val restored = repository.getTask(id)!!
            assertFalse("возврат снимает отметку", restored.completed)
            assertNull("момент выполнения не переживает возврат", restored.completedAt)
            assertEquals(target, restored.date)

            assertTrue(repository.observeCompletedTasks().first().isEmpty())
            assertEquals(listOf(id), repository.observeActiveTasksForDate(target).first().map { it.id })
        }

    @Test
    fun restoreToToday_landsOnTheDayItWasGiven() =
        runTest {
            val id = finish("Позвонить")

            restore(id, FINISHED_ON)

            assertEquals(FINISHED_ON, repository.getTask(id)!!.date)
        }

    @Test
    fun restoreADateLessTask_givesItTheChosenDay() =
        runTest {
            val id = finish("Полить цветы", isGlobal = true)
            val target = LocalDate.of(2026, 9, 2)

            restore(id, target)

            val restored = repository.getTask(id)!!
            assertFalse(restored.isGlobal)
            assertEquals(target, restored.date)
        }

    @Test
    fun restoreMovesOnlyTheTickAndTheDay() =
        runTest {
            val time = LocalTime.of(9, 30)
            val task = createTask("Сдать книгу", PLANNED_FOR, time, "в библиотеку", false)
            completeTask(task.id, completed = true)

            restore(task.id, FINISHED_ON)

            val restored = repository.getTask(task.id)!!
            assertEquals("Сдать книгу", restored.title)
            assertEquals(time, restored.time)
            assertEquals("в библиотеку", restored.note)
            assertEquals(task.createdAt, restored.createdAt)
            assertEquals(task.syncId, restored.syncId)
        }

    @Test
    fun restoreOfAnUnknownIdChangesNothing() =
        runTest {
            val id = finish("Купить хлеб")

            restore(id + 999, LocalDate.of(2026, 9, 2))

            val stored = repository.getTask(id)
            assertNotNull("вернувшееся дело никуда не делось", stored)
            assertEquals(PLANNED_FOR, stored!!.date)
            assertTrue("чужой id не должен ничего менять", stored.completed)
        }

    @Test
    fun restoreOfADeletedTaskLeavesItDeleted() =
        runTest {
            val id = finish("Разобрать почту")
            deleteTask(id)

            restore(id, LocalDate.of(2026, 9, 2))

            assertNotNull("надгробие не должно исчезнуть", repository.getTask(id)!!.deletedAt)
            assertTrue(repository.observeCompletedTasks().first().isEmpty())
            assertTrue(repository.observeActiveTasksForDate(LocalDate.of(2026, 9, 2)).first().isEmpty())
        }

    private companion object {
        val PLANNED_FOR: LocalDate = LocalDate.of(2026, 8, 20)
        val FINISHED_ON: LocalDate = LocalDate.of(2026, 8, 25)
    }
}
