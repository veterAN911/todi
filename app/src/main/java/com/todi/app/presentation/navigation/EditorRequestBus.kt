package com.todi.app.presentation.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditorRequestBus
    @Inject
    constructor() {
        private val _requestCount = MutableStateFlow(0L)
        val requestCount: StateFlow<Long> = _requestCount.asStateFlow()

        fun requestOpen() {
            _requestCount.value += 1
        }
    }
