package com.todi.app.presentation.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.todi.app.domain.util.TaskSearch

fun highlightMatches(
    text: String,
    query: String?,
    color: Color,
): AnnotatedString {
    val ranges = if (query.isNullOrBlank()) emptyList() else TaskSearch.matchRanges(text, query)
    if (ranges.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        append(text)
        ranges.forEach { range ->
            addStyle(
                style = SpanStyle(color = color, fontWeight = FontWeight.SemiBold),
                start = range.first,
                end = range.last + 1,
            )
        }
    }
}
