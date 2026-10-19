package com.todi.app.presentation.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.todi.app.R
import com.todi.app.presentation.about.AboutScreen
import com.todi.app.presentation.account.BackupAccountViewModel
import com.todi.app.presentation.account.BackupSignInDialog
import com.todi.app.presentation.calendar.CalendarScreen
import com.todi.app.presentation.calendar.CalendarViewModel
import com.todi.app.presentation.completed.CompletedScreen
import com.todi.app.presentation.completed.CompletedViewModel
import com.todi.app.presentation.components.BrandMark
import com.todi.app.presentation.day.DayScreen
import com.todi.app.presentation.day.TodayViewModel
import com.todi.app.presentation.day.TomorrowViewModel
import com.todi.app.presentation.editor.TaskEditorViewModel
import com.todi.app.presentation.global.GlobalTasksScreen
import com.todi.app.presentation.global.GlobalTasksViewModel
import com.todi.app.presentation.search.SearchScreen
import com.todi.app.presentation.search.SearchViewModel
import com.todi.app.presentation.settings.SettingsScreen
import com.todi.app.presentation.settings.SettingsViewModel
import com.todi.app.presentation.voice.ADD_BUTTON_LINE_GAP
import com.todi.app.presentation.voice.ADD_BUTTON_OVERHANG
import com.todi.app.presentation.voice.ADD_BUTTON_SHOULDER
import com.todi.app.presentation.voice.ADD_BUTTON_SINK
import com.todi.app.presentation.voice.ADD_BUTTON_SIZE
import com.todi.app.presentation.voice.AddButtonContour
import com.todi.app.presentation.voice.AddButtonRequest
import com.todi.app.presentation.voice.AddButtonSlot
import com.todi.app.presentation.voice.LocalAddButtonSlot
import com.todi.app.presentation.voice.VoiceAddFab
import kotlinx.coroutines.launch

@Composable
fun TodiApp(
    editorRequestBus: EditorRequestBus,
    bottomBarRoutes: List<String>?,
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val accountViewModel: BackupAccountViewModel = hiltViewModel()
    val account by accountViewModel.account.collectAsStateWithLifecycle()
    val signIn by accountViewModel.signIn.collectAsStateWithLifecycle()
    var showSignIn by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 600.dp
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        val barDestinations = remember(bottomBarRoutes) { bottomBarDestinations(bottomBarRoutes) }
        val openMenu: () -> Unit = { scope.launch { drawerState.open() } }

        fun navigateTo(route: String) {
            if (currentRoute == route) return
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    drawerShape =
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        DrawerHeader(
                            email = account?.email,
                            onSignIn = { showSignIn = true },
                        )

                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.search)) },
                            icon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            selected = currentRoute == SEARCH_ROUTE,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(SEARCH_ROUTE)
                            },
                            modifier =
                                Modifier
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .testTag(DRAWER_SEARCH_TAG),
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        TodiDestination.entries.forEach { destination ->
                            NavigationDrawerItem(
                                label = { Text(stringResource(destination.labelRes)) },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                selected = currentRoute == destination.route,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navigateTo(destination.route)
                                },
                                modifier =
                                    Modifier
                                        .padding(horizontal = 12.dp, vertical = 2.dp)
                                        .testTag(drawerTag(destination)),
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )

                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.nav_about)) },
                            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                            selected = currentRoute == ABOUT_ROUTE,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(ABOUT_ROUTE)
                            },
                            modifier =
                                Modifier
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .testTag(DRAWER_ABOUT_TAG),
                        )
                    }
                }
            },
        ) {
            if (expanded) {
                Row(Modifier.fillMaxSize()) {
                    if (barDestinations.isNotEmpty()) {
                        TodiRail(
                            destinations = barDestinations,
                            currentRoute = currentRoute,
                            onNavigate = { route -> navigateTo(route) },
                        )
                        VerticalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.fillMaxHeight(),
                        )
                    }
                    NavHost(
                        navController = navController,
                        startDestination = TodiDestination.TODAY.route,
                        modifier = Modifier.weight(1f),
                    ) {
                        routes(
                            editorRequestBus = editorRequestBus,
                            showMenuButton = true,
                            onOpenMenu = openMenu,
                            onOpenSearch = { navController.navigate(SEARCH_ROUTE) },
                        )
                        composable(SEARCH_ROUTE) {
                            SearchRoute(onBack = { navController.popBackStack() })
                        }
                        composable(ABOUT_ROUTE) {
                            AboutScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            } else {
                val addButton = remember { AddButtonSlot() }

                val docked = barDestinations.isNotEmpty()
                Scaffold(
                    bottomBar = {
                        if (docked) {
                            TodiBottomBar(
                                destinations = barDestinations,
                                currentRoute = currentRoute,
                                onNavigate = { route -> navigateTo(route) },
                                addButton = addButton.request,
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background,
                ) { padding ->
                    CompositionLocalProvider(LocalAddButtonSlot provides addButton.takeIf { docked }) {
                        NavHost(
                            navController = navController,
                            startDestination = TodiDestination.TODAY.route,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(padding),
                        ) {
                            routes(
                                editorRequestBus = editorRequestBus,
                                showMenuButton = true,
                                onOpenMenu = openMenu,
                                onOpenSearch = { navController.navigate(SEARCH_ROUTE) },
                            )
                            composable(SEARCH_ROUTE) {
                                SearchRoute(onBack = { navController.popBackStack() })
                            }
                            composable(ABOUT_ROUTE) {
                                AboutScreen(onBack = { navController.popBackStack() })
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        LaunchedEffect(account) {
            if (account != null) showSignIn = false
        }

        if (showSignIn) {
            BackupSignInDialog(
                awaitingCodeFor = signIn.awaitingCodeFor,
                busy = signIn.busy,
                onRequestCode = accountViewModel::requestCode,
                onVerify = accountViewModel::verifyCode,
                onBack = accountViewModel::cancelCode,
                onDismiss = {
                    showSignIn = false
                    accountViewModel.cancelCode()
                },
            )
        }
    }
}

private fun androidx.navigation.NavGraphBuilder.routes(
    editorRequestBus: EditorRequestBus,
    showMenuButton: Boolean,
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    composable(TodiDestination.TODAY.route) {
        TodayRoute(editorRequestBus, showMenuButton, onOpenMenu, onOpenSearch)
    }
    composable(TodiDestination.TOMORROW.route) {
        TomorrowRoute(showMenuButton, onOpenMenu, onOpenSearch)
    }
    composable(TodiDestination.GLOBAL.route) {
        GlobalRoute(showMenuButton, onOpenMenu, onOpenSearch)
    }
    composable(TodiDestination.CALENDAR.route) {
        CalendarRoute(showMenuButton, onOpenMenu, onOpenSearch)
    }
    composable(TodiDestination.COMPLETED.route) {
        CompletedRoute(showMenuButton, onOpenMenu, onOpenSearch)
    }
    composable(TodiDestination.SETTINGS.route) {
        SettingsRoute(showMenuButton, onOpenMenu, onOpenSearch)
    }
}

@Composable
private fun TodayRoute(
    editorRequestBus: EditorRequestBus,
    showMenuButton: Boolean,
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val viewModel: TodayViewModel = hiltViewModel()
    val editor: TaskEditorViewModel = hiltViewModel()
    val today by viewModel.today.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        editorRequestBus.requestCount.collect { count ->
            if (count > 0) editor.openForCreate(today)
        }
    }

    DayScreen(
        title = stringResource(R.string.nav_today),
        showMenuButton = showMenuButton,
        onOpenMenu = onOpenMenu,
        onOpenSearch = onOpenSearch,
        controller = viewModel.controller,
        editor = editor,
        actualToday = today,
    )
}

@Composable
private fun TomorrowRoute(
    showMenuButton: Boolean,
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val viewModel: TomorrowViewModel = hiltViewModel()
    val editor: TaskEditorViewModel = hiltViewModel()
    val today by viewModel.today.collectAsStateWithLifecycle()

    DayScreen(
        title = stringResource(R.string.nav_tomorrow),
        showMenuButton = showMenuButton,
        onOpenMenu = onOpenMenu,
        onOpenSearch = onOpenSearch,
        controller = viewModel.controller,
        editor = editor,
        actualToday = today,
    )
}

@Composable
private fun GlobalRoute(
    showMenuButton: Boolean,
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val viewModel: GlobalTasksViewModel = hiltViewModel()
    val editor: TaskEditorViewModel = hiltViewModel()

    GlobalTasksScreen(
        showMenuButton = showMenuButton,
        onOpenMenu = onOpenMenu,
        onOpenSearch = onOpenSearch,
        viewModel = viewModel,
        editor = editor,
    )
}

@Composable
private fun CalendarRoute(
    showMenuButton: Boolean,
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val viewModel: CalendarViewModel = hiltViewModel()
    val editor: TaskEditorViewModel = hiltViewModel()

    CalendarScreen(
        showMenuButton = showMenuButton,
        onOpenMenu = onOpenMenu,
        onOpenSearch = onOpenSearch,
        viewModel = viewModel,
        editor = editor,
    )
}

@Composable
private fun CompletedRoute(
    showMenuButton: Boolean,
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val viewModel: CompletedViewModel = hiltViewModel()
    val editor: TaskEditorViewModel = hiltViewModel()

    CompletedScreen(
        showMenuButton = showMenuButton,
        onOpenMenu = onOpenMenu,
        onOpenSearch = onOpenSearch,
        viewModel = viewModel,
        editor = editor,
    )
}

@Composable
private fun SettingsRoute(
    showMenuButton: Boolean,
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val viewModel: SettingsViewModel = hiltViewModel()

    SettingsScreen(
        showMenuButton = showMenuButton,
        onOpenMenu = onOpenMenu,
        onOpenSearch = onOpenSearch,
        viewModel = viewModel,
    )
}

@Composable
private fun SearchRoute(onBack: () -> Unit) {
    val viewModel: SearchViewModel = hiltViewModel()
    val editor: TaskEditorViewModel = hiltViewModel()

    SearchScreen(onBack = onBack, viewModel = viewModel, editor = editor)
}

@Composable
private fun DrawerHeader(
    email: String?,
    onSignIn: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 8.dp, top = 20.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(size = 40.dp)
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
        if (email == null) {
            TextButton(
                onClick = onSignIn,
                modifier = Modifier.testTag(DRAWER_SIGN_IN_TAG),
            ) {
                Text(stringResource(R.string.backup_account_sign_in), maxLines = 1)
            }
        } else {
            Text(
                text = email,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.testTag(DRAWER_ACCOUNT_TAG),
            )
        }
    }
}

@Composable
private fun TodiBottomBar(
    destinations: List<TodiDestination>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    addButton: AddButtonRequest?,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .testTag(BOTTOM_BAR_TAG),
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
        ) {
            destinations.forEach { destination ->
                NavigationBarItem(
                    selected = currentRoute == destination.route,
                    onClick = { onNavigate(destination.route) },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = {
                        Text(
                            text = stringResource(destination.bottomLabelRes),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    },
                    alwaysShowLabel = destinations.size <= LABELLED_BAR_MAX_ITEMS,
                    modifier = Modifier.testTag(bottomBarTag(destination)),
                )
            }
        }

        val lineColor = MaterialTheme.colorScheme.outlineVariant
        Canvas(Modifier.matchParentSize()) {
            val stroke = 1.dp.toPx()
            val path = Path()
            path.moveTo(0f, 0f)

            val centreX = size.width / 2f
            val hump =
                if (addButton == null) {
                    null
                } else {
                    AddButtonContour.hump(
                        radius = ADD_BUTTON_SIZE.toPx() / 2f,
                        sink = ADD_BUTTON_SINK.toPx(),
                        gap = ADD_BUTTON_LINE_GAP.toPx() + stroke / 2f,
                        shoulder = ADD_BUTTON_SHOULDER.toPx(),
                    )
                }
            if (hump != null) {
                path.lineTo(centreX - hump.straightEnd, 0f)
                hump.arcs.forEach { arc ->
                    path.arcTo(
                        rect =
                            Rect(
                                left = centreX + arc.centreX - arc.radius,
                                top = arc.centreY - arc.radius,
                                right = centreX + arc.centreX + arc.radius,
                                bottom = arc.centreY + arc.radius,
                            ),
                        startAngleDegrees = arc.startDegrees,
                        sweepAngleDegrees = arc.sweepDegrees,
                        forceMoveTo = false,
                    )
                }
            }
            path.lineTo(size.width, 0f)
            drawPath(path, color = lineColor, style = Stroke(width = stroke))
        }
        addButton?.let { request ->

            VoiceAddFab(
                targetDate = request.targetDate,
                docked = true,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = -ADD_BUTTON_OVERHANG),
                onTap = request.onTap,
            )
        }
    }
}

@Composable
private fun TodiRail(
    destinations: List<TodiDestination>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        header = {
            Box(Modifier.padding(vertical = 16.dp)) {
                BrandMark(size = 36.dp)
            }
        },
    ) {
        destinations.forEach { destination ->
            NavigationRailItem(
                selected = currentRoute == destination.route,
                onClick = { onNavigate(destination.route) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.labelRes)) },
                colors =
                    NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                    ),
            )
        }
    }
}

private const val LABELLED_BAR_MAX_ITEMS = 4
