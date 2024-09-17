package com.github.ShinkaiKung.verbalkiller

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.github.ShinkaiKung.verbalkiller.practice.PracticeLayout

enum class TopLevelDestination(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val iconTextId: String,
    val titleTextId: String,
    val route: String
) {
    Practice(
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        iconTextId = "Practice",
        titleTextId = "Practice",
        route = "Practice",
    ),
    Info(
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        iconTextId = "Info",
        titleTextId = "Info",
        route = "Info",
    ),
}

fun NavController.navigateToPractice(navOptions: NavOptions) =
    navigate(TopLevelDestination.Practice.route, navOptions)

fun NavGraphBuilder.practiceScreen() {
    composable(route = TopLevelDestination.Practice.route) {
        PracticeLayout()
    }
}

fun NavController.navigateToInfo(navOptions: NavOptions) =
    navigate(TopLevelDestination.Info.route, navOptions)

fun NavGraphBuilder.infoScreen() {
    composable(route = TopLevelDestination.Info.route) {
        InfoLayout()
    }
}

fun navigateToTopLevelDestination(
    navController: NavController,
    topLevelDestination: TopLevelDestination
) {
    val topLevelNavOptions = navOptions {
        // Pop up to the start destination of the graph to
        // avoid building up a large stack of destinations
        // on the back stack as users select items
        popUpTo(navController.graph.get(TopLevelDestination.Practice.route).id) {
            saveState = true
        }
        // Avoid multiple copies of the same destination when
        // reselecting the same item
        launchSingleTop = true
        // Restore state when reselecting a previously selected item
        restoreState = true
    }

    when (topLevelDestination) {
        TopLevelDestination.Practice -> navController.navigateToPractice(topLevelNavOptions)
        TopLevelDestination.Info -> navController.navigateToInfo(topLevelNavOptions)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavLayout(navController: NavHostController) {
    Scaffold(
        modifier = Modifier,
        bottomBar = {
            if (navController.currentBackStackEntryAsState().value?.destination?.route in TopLevelDestination.entries.map { it.route }) {
                NavBottomBar(navController = navController)
            }
        }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val currentDestination =
                navController.currentBackStackEntryAsState().value?.destination
            val currentTopLevelDestination = when (currentDestination?.route) {
                TopLevelDestination.Practice.route -> TopLevelDestination.Practice
                TopLevelDestination.Info.route -> TopLevelDestination.Info
                else -> null
            }
            if (currentTopLevelDestination != null) {
                CenterAlignedTopAppBar(
                    title = { Text(text = currentTopLevelDestination.titleTextId, style = MaterialTheme.typography.headlineSmall) },
                )
            }
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.Practice.route
            ) {
                practiceScreen()
                infoScreen()
            }

        }

    }

}

@Composable
fun NavBottomBar(navController: NavHostController) {
    NavigationBar(modifier = Modifier) {
        val destinations = TopLevelDestination.entries
        destinations.forEach { destination ->
            val selected =
                navController.currentBackStackEntryAsState().value?.destination?.route == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { navigateToTopLevelDestination(navController, destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null
                    )
                },
                label = { Text(text = destination.iconTextId) }
            )
        }
    }
}