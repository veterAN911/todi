package com.todi.app.domain

import com.todi.app.domain.model.AppSettings
import com.todi.app.domain.model.Task
import com.todi.app.domain.usecase.CompletedArchiveCleanup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CompletedArchiveCleanupTest {
    private val zone = ZoneId.of("Europe/Berlin")

    private val now: Instant = Instant.parse("2026-06-01T10:00:00Z")

    private val repository = FakeTaskRepository()
    private val settings = FakeSettingsRepository()
    private val cleanup =
        CompletedArchiveCleanup(
            repository = repository,
            settingsRepository = settings,
            clock = Clock.fixed(now, zone),
        )

    private suspend fun addCompleted(
        title: String,
        completedAt: Instant?,
    ): Task {
        val task = repository.createTask(title, LocalDate.of(2026, 1, 1), null, null)
        repository.updateTask(task.copy(completed = true, completedAt = completedAt))
        return task
    }

    private suspend fun titles() =
        repository
            .observeAllTasks()
            .first()
            .map { it.title }
            .sorted()

    @Test
    fun `forever keeps even very old completed tasks`() =
        runTest {
            addCompleted("старое", Instant.parse("2020-01-01T00:00:00Z"))

            assertEquals(0, cleanup.perform())
            assertEquals(listOf("старое"), titles())
        }

    @Test
    fun `a term deletes only what is older than the cutoff`() =
        runTest {
            settings.setCompletedRetentionMonths(3)
            addCompleted("февральское", Instant.parse("2026-02-15T10:00:00Z"))
            addCompleted("майское", Instant.parse("2026-05-20T10:00:00Z"))

            val removed = cleanup.perform()

            assertEquals(1, removed)
            assertEquals(listOf("майское"), titles())
        }

    @Test
    fun `a task completed exactly on the cutoff is kept`() =
        runTest {
            settings.setCompletedRetentionMonths(1)
            addCompleted("ровно", Instant.parse("2026-05-01T10:00:00Z"))

            assertEquals(0, cleanup.perform())
            assertEquals(listOf("ровно"), titles())
        }

    @Test
    fun `completed tasks without a completion time are never auto-deleted`() =
        runTest {
            settings.setCompletedRetentionMonths(1)
            addCompleted("без времени", completedAt = null)

            assertEquals(0, cleanup.perform())
            assertEquals(listOf("без времени"), titles())
        }

    @Test
    fun `active tasks are untouched by retention`() =
        runTest {
            settings.setCompletedRetentionMonths(1)
            repository.createTask("активное", LocalDate.of(2020, 1, 1), null, null)

            assertEquals(0, cleanup.perform())
            assertEquals(listOf("активное"), titles())
        }

    @Test
    fun `clearAll removes the archive but keeps active tasks`() =
        runTest {
            addCompleted("свежее", Instant.parse("2026-05-31T10:00:00Z"))
            addCompleted("без времени", completedAt = null)
            repository.createTask("активное", LocalDate.of(2026, 5, 31), null, null)

            val removed = cleanup.clearAll()

            assertEquals(2, removed)
            assertEquals(listOf("активное"), titles())
        }

    @Test
    fun `a term is read from settings on every run`() =
        runTest {
            addCompleted("апрельское", Instant.parse("2026-04-15T10:00:00Z"))

            assertEquals(0, cleanup.perform())
            assertEquals(listOf("апрельское"), titles())

            settings.setCompletedRetentionMonths(1)
            assertEquals(1, cleanup.perform())
            assertTrue(titles().isEmpty())
        }

    @Test
    fun `settings default to keeping everything`() {
        assertEquals(0, AppSettings().completedRetentionMonths)
    }
}
