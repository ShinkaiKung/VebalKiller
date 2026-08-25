package com.github.ShinkaiKung.verbalkiller

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.get
import androidx.navigation.navOptions
import com.github.ShinkaiKung.verbalkiller.info.InfoLayout
import com.github.ShinkaiKung.verbalkiller.info.ProgressViewModel
import com.github.ShinkaiKung.verbalkiller.practice.PracticeLayout
import com.github.ShinkaiKung.verbalkiller.practice.PracticeViewModel

enum class TopLevelDestination(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String,
    val title: String,
    val route: String,
) {
    Practice(
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        label = "练习",
        title = "Pair Drill",
        route = "practice",
    ),
    Progress(
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        label = "进度",
        title = "强化进度",
        route = "progress",
    ),
}

fun NavController.navigateToPractice(navOptions: NavOptions) =
    navigate(TopLevelDestination.Practice.route, navOptions)

fun NavController.navigateToProgress(navOptions: NavOptions) =
    navigate(TopLevelDestination.Progress.route, navOptions)

private fun NavGraphBuilder.practiceScreen(viewModel: PracticeViewModel) {
    composable(route = TopLevelDestination.Practice.route) { PracticeLayout(viewModel) }
}

private fun NavGraphBuilder.progressScreen(viewModel: ProgressViewModel) {
    composable(route = TopLevelDestination.Progress.route) { InfoLayout(viewModel) }
}

private fun navigateToTopLevelDestination(
    navController: NavController,
    destination: TopLevelDestination,
) {
    val options = navOptions {
        popUpTo(navController.graph.get(TopLevelDestination.Practice.route).id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
    when (destination) {
        TopLevelDestination.Practice -> navController.navigateToPractice(options)
        TopLevelDestination.Progress -> navController.navigateToProgress(options)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavLayout(
    navController: NavHostController,
    practiceViewModel: PracticeViewModel,
    progressViewModel: ProgressViewModel,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentDestination = TopLevelDestination.entries.firstOrNull { it.route == currentRoute }
        ?: TopLevelDestination.Practice

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(currentDestination.title, style = MaterialTheme.typography.headlineSmall)
                },
            )
        },
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateToTopLevelDestination(navController, destination) },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Practice.route,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            practiceScreen(practiceViewModel)
            progressScreen(progressViewModel)
        }
    }
}
