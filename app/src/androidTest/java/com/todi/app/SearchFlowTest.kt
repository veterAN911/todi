package com.todi.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.todi.app.data.local.AppDatabase
import com.todi.app.domain.repository.TaskRepository
import com.todi.app.domain.util.SearchSection
import com.todi.app.presentation.MainActivity
import com.todi.app.presentation.navigation.DRAWER_SEARCH_TAG
import com.todi.app.presentation.search.SEARCH_FIELD_TAG
import com.todi.app.presentation.search.searchGroupTag
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltAndroidTest
class SearchFlowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var repository: TaskRepository

    private val today = LocalDate.now()

    private fun str(id: Int): String = composeRule.activity.getString(id)

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking { database.clearAllTables() }
        composeRule.waitForIdle()
        dismissOnboardingIfShown()
    }

    private fun dismissOnboardingIfShown() {
        val laterText = str(R.string.later)
        val todayText = str(R.string.nav_today)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(laterText).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText(todayText).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithText(laterText).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText(laterText).performClick()
            composeRule.waitForIdle()
        }
    }

    private fun seed(
        title: String,
        date: LocalDate = today,
        time: LocalTime? = null,
        note: String? = null,
        isGlobal: Boolean = false,
        completed: Boolean = false,
    ) = runBlocking {
        val task = repository.createTask(title, date, time, note, isGlobal)
        if (completed) repository.setCompleted(task.id, true)
        composeRule.waitForIdle()
        task
    }

    private fun openSearch() {
        composeRule.onNodeWithContentDescription(str(R.string.cd_search)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(SEARCH_FIELD_TAG).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun searchFor(
        query: String,
        expected: String,
    ) {
        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput(query)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithText(expected, substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun assertSection(
        section: SearchSection,
        shown: Boolean,
    ) {
        val nodes = composeRule.onAllNodesWithTag(searchGroupTag(section)).fetchSemanticsNodes()
        if (shown) {
            check(nodes.size == 1) { "секция $section: ожидался один заголовок, найдено ${nodes.size}" }
        } else {
            check(nodes.isEmpty()) { "секция $section не должна показываться" }
        }
    }

    private fun assertNotShown(text: String) {
        check(composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()) {
            "«$text» не должно быть на экране"
        }
    }

    @Test
    fun magnifier_opensSearchWithAPromptRatherThanResults() {
        openSearch()

        composeRule.onNodeWithText(str(R.string.search_prompt_title)).assertIsDisplayed()
        assertNotShown(str(R.string.search_nothing_found))
        SearchSection.entries.forEach { assertSection(it, shown = false) }
    }

    @Test
    fun findsTasksOnEveryDay_notOnlyTheOneOnScreen() {
        seed("Купить хлеб")
        seed("Хлеб на закваске", date = today.plusDays(3))
        seed("Позвонить про хлеб", date = today.minusDays(5))

        openSearch()
        searchFor("хлеб", "Хлеб на закваске")

        composeRule.onNodeWithText("Купить хлеб").assertIsDisplayed()
        composeRule.onNodeWithText("Позвонить про хлеб").assertIsDisplayed()
        assertSection(SearchSection.TODAY, shown = true)
        assertSection(SearchSection.DATED, shown = true)
        assertSection(SearchSection.GLOBAL, shown = false)
        assertSection(SearchSection.COMPLETED, shown = false)
    }

    @Test
    fun findsDateLessAndCompletedTasks_withTheirOwnSections() {
        seed("Хлеб без даты", isGlobal = true)
        seed("Съесть хлеб", completed = true)

        openSearch()
        searchFor("хлеб", "Хлеб без даты")

        assertSection(SearchSection.GLOBAL, shown = true)
        assertSection(SearchSection.COMPLETED, shown = true)
        composeRule.onNodeWithText("Хлеб без даты").assertIsDisplayed()
        composeRule.onNodeWithText("Съесть хлеб").assertIsDisplayed()
    }

    @Test
    fun dateLessTaskInTheArchive_carriesItsOwnLabel() {
        seed("Хлеб без даты съеден", isGlobal = true, completed = true)

        openSearch()
        searchFor("хлеб", "Хлеб без даты съеден")

        assertSection(SearchSection.COMPLETED, shown = true)

        composeRule
            .onAllNodesWithText(str(R.string.editor_without_date))
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun matchesByNote_notOnlyByTitle() {
        seed("Магазин", note = "не забыть хлеб и соль")
        seed("Аптека")

        openSearch()
        searchFor("хлеб", "Магазин")

        composeRule.onNodeWithText("Магазин").assertIsDisplayed()
        assertNotShown("Аптека")
    }

    @Test
    fun nothingFound_showsTheEmptyState_insteadOfThePrompt() {
        seed("Купить хлеб")

        openSearch()
        searchFor("велосипед", str(R.string.search_nothing_found))

        assertNotShown(str(R.string.search_prompt_title))
        assertNotShown("Купить хлеб")
    }

    @Test
    fun deletingFromTheResults_canBeUndone() {
        seed("Купить хлеб")

        openSearch()
        searchFor("хлеб", "Купить хлеб")
        composeRule.onNodeWithContentDescription(str(R.string.cd_more_options)).performClick()
        composeRule.onNodeWithText(str(R.string.delete_task)).performClick()

        composeRule.onNodeWithText(str(R.string.task_deleted)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.undo)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Купить хлеб").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun editingFromTheResults_updatesTheTaskInsteadOfDuplicatingIt() {
        seed("Купить хлеб")

        openSearch()
        searchFor("хлеб", "Купить хлеб")
        composeRule.onNodeWithContentDescription(str(R.string.cd_more_options)).performClick()
        composeRule.onNodeWithText(str(R.string.edit_task)).performClick()
        composeRule.onNodeWithText(str(R.string.save)).assertIsDisplayed()

        composeRule
            .onNode(hasSetTextAction() and hasText("Купить хлеб"))
            .performTextReplacement("Купить хлеб и молоко")
        composeRule.onNodeWithText(str(R.string.save)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Купить хлеб и молоко").fetchSemanticsNodes().size == 1
        }
        assertNotShown("Купить хлеб")
    }

    @Test
    fun backArrow_leavesSearch() {
        openSearch()

        composeRule.onNodeWithContentDescription(str(R.string.cd_back)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(SEARCH_FIELD_TAG).fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithContentDescription(str(R.string.cd_add_task)).assertIsDisplayed()
    }

    @Test
    fun drawerEntry_opensSearch() {
        composeRule.onNodeWithContentDescription(str(R.string.cd_open_menu)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DRAWER_SEARCH_TAG).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(DRAWER_SEARCH_TAG).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(SEARCH_FIELD_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(str(R.string.search_prompt_title)).assertIsDisplayed()
    }
}
