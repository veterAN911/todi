package com.todi.app.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Upcoming
import androidx.compose.ui.graphics.vector.ImageVector
import com.todi.app.R
import com.todi.app.domain.util.NavBarRoutes

enum class TodiDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    @StringRes val bottomLabelRes: Int = labelRes,
) {
    TODAY("today", R.string.nav_today, Icons.Outlined.Today),
    TOMORROW("tomorrow", R.string.nav_tomorrow, Icons.Outlined.Upcoming),
    GLOBAL("global", R.string.nav_global, Icons.Outlined.AllInbox, R.string.nav_bottom_global),
    CALENDAR("calendar", R.string.nav_calendar, Icons.Outlined.CalendarMonth),
    COMPLETED("completed", R.string.nav_completed, Icons.Outlined.TaskAlt, R.string.nav_bottom_completed),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings),
}

val ALL_ROUTES: List<String> = TodiDestination.entries.map { it.route }

val DEFAULT_BOTTOM_BAR_ROUTES: List<String> =
    listOf(
        TodiDestination.TODAY.route,
        TodiDestination.TOMORROW.route,
        TodiDestination.CALENDAR.route,
        TodiDestination.COMPLETED.route,
    )

fun bottomBarDestinations(stored: List<String>?): List<TodiDestination> =
    NavBarRoutes
        .resolve(stored, DEFAULT_BOTTOM_BAR_ROUTES, ALL_ROUTES)
        .mapNotNull { route -> TodiDestination.entries.firstOrNull { it.route == route } }

const val SEARCH_ROUTE = "search"

const val DRAWER_SEARCH_TAG = "drawer_search"

const val ABOUT_ROUTE = "about"

const val DRAWER_ABOUT_TAG = "drawer_about"

const val DRAWER_SIGN_IN_TAG = "drawer_sign_in"

const val DRAWER_ACCOUNT_TAG = "drawer_account"

fun bottomBarTag(destination: TodiDestination): String = "nav_bottom_${destination.route}"

fun drawerTag(destination: TodiDestination): String = "drawer_nav_${destination.route}"

const val BOTTOM_BAR_TAG = "bottomBar"
