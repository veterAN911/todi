package com.todi.app.domain.util

object NavBarRoutes {
    fun decode(stored: String?): List<String>? {
        if (stored == null) return null
        return stored
            .split(SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun encode(routes: List<String>): String = routes.filter { it.isNotBlank() }.joinToString(SEPARATOR)

    fun resolve(
        stored: List<String>?,
        defaults: List<String>,
        known: List<String>,
    ): List<String> {
        val wanted = stored ?: defaults
        return known.filter { it in wanted }
    }

    fun toggle(
        stored: List<String>?,
        route: String,
        on: Boolean,
        defaults: List<String>,
        known: List<String>,
    ): List<String> {
        val updated = if (on) resolve(stored, defaults, known) + route else resolve(stored, defaults, known) - route
        return known.filter { it in updated }
    }

    private const val SEPARATOR = ","
}
