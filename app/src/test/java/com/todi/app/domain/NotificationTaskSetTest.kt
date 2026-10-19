package com.todi.app.domain

import com.todi.app.domain.model.NotificationScope
import com.todi.app.domain.usecase.GetActiveTasksForNotificationUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class NotificationTaskSetTest {
    private val today = LocalDate.of(2026, 9, 12)

    private val repository = FakeTaskRepository()
    private val settings = FakeSettingsRepository()
    private val todayProvider = FakeTodayProvider(today)
    private val useCase = GetActiveTasksForNotificationUseCase(repository, settings, todayProvider)

    @Test
    fun `today's tasks are listed`() =
        runTest {
            repository.createTask("сегодняшнее", today, LocalTime.of(9, 0), null)

            val set = useCase().first()

            assertEquals(listOf("сегодняшнее"), set.today.map { it.title })
            assertEquals(emptyList<String>(), set.overdue.map { it.title })
            assertEquals(emptyList<String>(), set.dateLess.map { it.title })
        }

    @Test
    fun `date-less tasks stay out of the daily notification below the widest scope`() =
        runTest {
            repository.createTask("позвонить", today, null, null, isGlobal = true)
            repository.createTask("сегодняшнее", today, LocalTime.of(9, 0), null)

            assertEquals(emptyList<String>(), useCase().first().dateLess.map { it.title })

            settings.setNotificationScope(NotificationScope.WITH_OVERDUE)

            val set = useCase().first()
            assertEquals(listOf("сегодняшнее"), set.today.map { it.title })
            assertEquals(emptyList<String>(), set.dateLess.map { it.title })
        }

    @Test
    fun `the widest scope lists date-less tasks as a group of their own`() =
        runTest {
            repository.createTask("позвонить", today, null, null, isGlobal = true)
            repository.createTask("сегодняшнее", today, LocalTime.of(9, 0), null)
            settings.setNotificationScope(NotificationScope.WITH_OVERDUE_AND_GLOBAL)

            val set = useCase().first()

            assertEquals(listOf("сегодняшнее"), set.today.map { it.title })
            assertEquals(listOf("позвонить"), set.dateLess.map { it.title })
        }

    @Test
    fun `overdue tasks are opt-in`() =
        runTest {
            repository.createTask("вчерашнее", today.minusDays(1), null, null)

            assertEquals(emptyList<String>(), useCase().first().overdue.map { it.title })

            settings.setNotificationScope(NotificationScope.WITH_OVERDUE)

            assertEquals(listOf("вчерашнее"), useCase().first().overdue.map { it.title })
        }

    @Test
    fun `a carried-over task is overdue, not one of today's`() =
        runTest {
            repository.createTask("вчерашнее", today.minusDays(1), null, null)
            repository.carryOverIncompleteTasks(today)
            settings.setNotificationScope(NotificationScope.WITH_OVERDUE)

            val set = useCase().first()

            assertEquals(emptyList<String>(), set.today.map { it.title })
            assertEquals(listOf("вчерашнее"), set.overdue.map { it.title })
        }

    @Test
    fun `a carried-over task stays among today's without the overdue group`() =
        runTest {
            repository.createTask("вчерашнее", today.minusDays(1), null, null)
            repository.carryOverIncompleteTasks(today)

            val set = useCase().first()

            assertEquals(listOf("вчерашнее"), set.today.map { it.title })
            assertEquals(emptyList<String>(), set.overdue.map { it.title })
        }

    @Test
    fun `carried-over and still-dated-before-today tasks form one overdue group`() =
        runTest {
            repository.createTask("перенесённое", today.minusDays(1), null, null)
            repository.carryOverIncompleteTasks(today)
            repository.createTask("оставшееся вчера", today.minusDays(1), null, null)
            settings.setNotificationScope(NotificationScope.WITH_OVERDUE)

            val set = useCase().first()

            assertEquals(emptyList<String>(), set.today.map { it.title })
            assertEquals(setOf("перенесённое", "оставшееся вчера"), set.overdue.map { it.title }.toSet())
        }

    @Test
    fun `changing the scope does not lose today's tasks`() =
        runTest {
            repository.createTask("сегодняшнее", today, LocalTime.of(9, 0), null)

            settings.setNotificationScope(NotificationScope.WITH_OVERDUE_AND_GLOBAL)
            settings.setNotificationScope(NotificationScope.TODAY_ONLY)

            assertEquals(listOf("сегодняшнее"), useCase().first().today.map { it.title })
        }

    @Test
    fun `completed tasks never reach the notification`() =
        runTest {
            val task = repository.createTask("сделанное", today, null, null)
            repository.setCompleted(task.id, true)

            assertEquals(emptyList<String>(), useCase().first().today.map { it.title })
        }
}
