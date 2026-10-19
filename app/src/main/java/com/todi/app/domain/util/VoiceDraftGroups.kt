package com.todi.app.domain.util

import java.time.LocalDate

enum class VoiceDraftDay { TODAY, TOMORROW, OTHER }

data class VoiceDraftGroup(
    val date: LocalDate?,
    val drafts: List<ParsedTask>,
    val day: VoiceDraftDay,
)

object VoiceDraftGroups {
    fun of(
        drafts: List<ParsedTask>,
        targetDate: LocalDate?,
        today: LocalDate,
    ): List<VoiceDraftGroup> =
        drafts
            .groupBy { it.dayOn(targetDate) }
            .map { (date, group) -> VoiceDraftGroup(date, group, dayOf(date, today)) }
            .sortedWith(compareBy(nullsLast()) { group: VoiceDraftGroup -> group.date })

    private fun dayOf(
        date: LocalDate?,
        today: LocalDate,
    ): VoiceDraftDay =
        when (date) {
            today -> VoiceDraftDay.TODAY
            today.plusDays(1) -> VoiceDraftDay.TOMORROW
            else -> VoiceDraftDay.OTHER
        }

    private fun ParsedTask.dayOn(targetDate: LocalDate?): LocalDate? = date ?: targetDate?.plusDays(relativeDayShift.toLong())
}
