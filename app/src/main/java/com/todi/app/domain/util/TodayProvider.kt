package com.todi.app.domain.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface TodayProvider {
    val today: StateFlow<LocalDate>

    fun refresh()
}

@Singleton
class SystemTodayProvider
    @Inject
    constructor() : TodayProvider {
        private val _today = MutableStateFlow(currentDate())
        override val today: StateFlow<LocalDate> = _today.asStateFlow()

        override fun refresh() {
            _today.value = currentDate()
        }

        private fun currentDate(): LocalDate = LocalDate.now()
    }
