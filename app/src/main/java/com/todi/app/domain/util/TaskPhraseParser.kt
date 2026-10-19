package com.todi.app.domain.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

data class ParsedTask(
    val date: LocalDate?,
    val time: LocalTime?,
    val title: String,
    val datePhrase: String?,
    val timePhrase: String?,
    val relativeDayShift: Int = 0,
)

enum class VoiceLanguage {
    RUSSIAN,
    OTHER,
    ;

    companion object {
        fun of(languageTag: String): VoiceLanguage = if (languageTag.startsWith("ru", ignoreCase = true)) RUSSIAN else OTHER
    }
}

object TaskPhraseParser {
    private val SEQUENCE_MARKERS = setOf("потом", "затем", "также", "далее")

    private val CONDITIONAL_MARKERS = setOf("и", "ещё", "еще")

    private const val SEQUENCE_HEAD = "после"
    private const val SEQUENCE_TAIL = "этого"

    private val IMPERATIVES =
        setOf(
            "позвони",
            "позвоните",
            "купи",
            "купите",
            "оплати",
            "оплатите",
            "запишись",
            "запишитесь",
            "сходи",
            "сходите",
            "проверь",
            "проверьте",
            "забери",
            "заберите",
            "отправь",
            "отправьте",
            "напиши",
            "напишите",
            "сделай",
            "сделайте",
            "закажи",
            "закажите",
            "узнай",
            "узнайте",
            "встреться",
            "напомни",
            "напомните",
            "посмотри",
            "посмотрите",
            "почисти",
            "погладь",
            "выучи",
            "прочитай",
            "прочитайте",
            "забронируй",
            "подготовь",
            "подготовьте",
            "выключи",
            "включи",
            "зайди",
            "зайдите",
            "приготовь",
            "постирай",
            "возьми",
            "положи",
            "отнеси",
            "принеси",
            "сдай",
            "заплати",
            "убери",
            "помой",
            "разбери",
            "проведи",
            "оформи",
            "зарегистрируй",
            "загрузи",
            "скачай",
            "закачай",
            "передай",
            "подари",
            "идти",
            "лечь",
            "печь",
            "мыть",
            "пить",
            "шить",
            "брить",
            "крыть",
        )

    private val INFINITIVE_LOOKALIKES =
        setOf(
            "почти",
            "кстати",
            "пути",
            "сети",
            "дети",
            "мать",
            "путь",
            "сеть",
            "тетрадь",
            "кровать",
            "нефть",
            "часть",
            "весть",
            "треть",
            "четверть",
            "новость",
            "радость",
            "очередь",
            "площадь",
            "мебель",
            "жизнь",
            "грязь",
        )

    private const val MIN_INFINITIVE_LENGTH = 5

    private val DATE_STARTERS = setOf("сегодня", "завтра", "послезавтра", "через")

    private const val SENTENCE_END = ".;!?"

    private val SPACE_BEFORE_PUNCTUATION = Regex("""\s+([,.;:!?])""")
    private val WHITESPACE = Regex("""\s+""")
    private val TITLE_EDGE_PUNCTUATION =
        charArrayOf(',', '.', ';', ':', '!', '?', ' ', '-', '–', '—', '«', '»', '(', ')')

    fun parse(
        transcript: String,
        now: ZonedDateTime,
        language: VoiceLanguage = VoiceLanguage.RUSSIAN,
    ): List<ParsedTask> {
        val text = transcript.trim()
        if (text.isEmpty()) return emptyList()
        if (language != VoiceLanguage.RUSSIAN) {
            return listOf(
                ParsedTask(
                    date = null,
                    time = null,
                    title = text,
                    datePhrase = null,
                    timePhrase = null,
                ),
            )
        }

        val tasks = mutableListOf<ParsedTask>()
        var inheritedDate: LocalDate? = null
        for (segment in segments(text)) {
            val task = parseSegment(segment, now, inheritedDate) ?: continue
            inheritedDate = task.date
            tasks += task
        }
        return tasks
    }

    private fun segments(text: String): List<String> {
        val tokens = tokenizeSpoken(text)
        if (tokens.isEmpty()) return emptyList()

        val segments = mutableListOf<String>()
        var start = 0
        var index = 0
        while (index < tokens.size) {
            if (index > start) {
                val core = tokens[index].core
                val twoWordMarker =
                    core == SEQUENCE_HEAD &&
                        tokens.coreAt(index + 1) == SEQUENCE_TAIL
                val markerWord =
                    twoWordMarker ||
                        core.isIn(SEQUENCE_MARKERS) ||
                        (core.isIn(CONDITIONAL_MARKERS) && startsTask(tokens, index + 1))

                if (markerWord) {
                    segments += slice(text, tokens, start, index - 1)
                    start = if (twoWordMarker) index + 2 else index + 1
                    index = start
                    continue
                }

                val punctuationSplit =
                    endsWithSentenceEnd(tokens[index]) ||
                        (endsWithComma(tokens[index]) && startsSegmentAfterComma(tokens, index + 1))
                if (punctuationSplit) {
                    segments += slice(text, tokens, start, index)
                    start = index + 1
                }
            }
            index++
        }
        if (start < tokens.size) segments += slice(text, tokens, start, tokens.size - 1)
        return segments.filter { it.isNotBlank() }
    }

    private fun slice(
        text: String,
        tokens: List<SpokenToken>,
        from: Int,
        to: Int,
    ): String {
        val first = tokens[from].range.first
        val last = tokens[to].range.last + 1
        return text.substring(first.coerceAtMost(last), last)
    }

    private fun endsWithSentenceEnd(token: SpokenToken): Boolean = token.raw.isNotEmpty() && token.raw.last() in SENTENCE_END

    private fun endsWithComma(token: SpokenToken): Boolean = token.raw.isNotEmpty() && token.raw.last() == ','

    private fun startsTask(
        tokens: List<SpokenToken>,
        index: Int,
    ): Boolean = startsVerb(tokens, index) || tokens.coreAt(index).isIn(DATE_STARTERS)

    private fun startsSegmentAfterComma(
        tokens: List<SpokenToken>,
        index: Int,
    ): Boolean =
        startsVerb(tokens, index) ||
            (tokens.coreAt(index).isIn(DATE_STARTERS) && startsVerb(tokens, index + 1))

    private fun startsVerb(
        tokens: List<SpokenToken>,
        index: Int,
    ): Boolean {
        val core = tokens.coreAt(index) ?: return false
        if (core.isEmpty()) return false
        if (core in IMPERATIVES) return true
        if (core in INFINITIVE_LOOKALIKES) return false

        if (core in SpokenLexicon.CARDINALS) return false
        return core.length >= MIN_INFINITIVE_LENGTH && core.endsWith("ть")
    }

    private fun parseSegment(
        segment: String,
        now: ZonedDateTime,
        inheritedDate: LocalDate?,
    ): ParsedTask? {
        val dateMatch = SpokenDateParser.findDate(segment, now)
        val timeMatch = SpokenDateParser.findTime(segment, now)

        val title = cleanTitle(segment, listOfNotNull(dateMatch?.range, timeMatch?.range))

        if (title.isBlank()) return null

        val date = dateMatch?.value ?: inheritedDate
        return ParsedTask(
            date = date,
            time = timeMatch?.value?.time,
            title = title,
            datePhrase = dateMatch?.let { phraseAt(segment, it.range) },
            timePhrase = timeMatch?.let { phraseAt(segment, it.range) },
            relativeDayShift = if (date == null) timeMatch?.value?.dayShift ?: 0 else 0,
        )
    }

    private fun phraseAt(
        text: String,
        range: IntRange,
    ): String = text.substring(range.first, range.last + 1).trim()

    private fun cleanTitle(
        text: String,
        removed: List<IntRange>,
    ): String {
        val kept = StringBuilder()
        var cursor = 0
        for (range in removed.sortedBy { it.first }) {
            if (range.last < cursor) continue
            if (range.first > cursor) kept.append(text, cursor, range.first)
            cursor = range.last + 1
        }
        if (cursor < text.length) kept.append(text, cursor, text.length)

        return kept
            .toString()
            .replace(SPACE_BEFORE_PUNCTUATION, "$1")
            .replace(WHITESPACE, " ")
            .trim(*TITLE_EDGE_PUNCTUATION)
    }

    private fun String?.isIn(set: Set<String>): Boolean = this != null && this in set

    private fun List<SpokenToken>.coreAt(index: Int): String? = getOrNull(index)?.core
}
