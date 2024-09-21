package com.github.ShinkaiKung.verbalkiller

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
import com.github.ShinkaiKung.verbalkiller.practice.getAllGroups
import com.github.ShinkaiKung.verbalkiller.practice.getSubGroupsWithErrorState
import com.github.ShinkaiKung.verbalkiller.practice.getSubGroupsWithIndex
import com.github.ShinkaiKung.verbalkiller.practice.getUnpracticedGroups
import com.github.ShinkaiKung.verbalkiller.practice.globalGroups
import com.github.ShinkaiKung.verbalkiller.practice.globalSubGroupsDesc

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
    navController: NavController, topLevelDestination: TopLevelDestination
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
    val isDropDownMenuOpen = remember { mutableStateOf(false) }

    Scaffold(modifier = Modifier, bottomBar = {
        if (navController.currentBackStackEntryAsState().value?.destination?.route in TopLevelDestination.entries.map { it.route }) {
            NavBottomBar(navController = navController)
        }
    }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val currentDestination = navController.currentBackStackEntryAsState().value?.destination
            val currentTopLevelDestination = when (currentDestination?.route) {
                TopLevelDestination.Practice.route -> TopLevelDestination.Practice
                TopLevelDestination.Info.route -> TopLevelDestination.Info
                else -> null
            }
            if (currentTopLevelDestination != null) {
                if (currentTopLevelDestination == TopLevelDestination.Practice) {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = currentTopLevelDestination.titleTextId,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        },
                    )
                }
                if (currentTopLevelDestination == TopLevelDestination.Info) {
                    CenterAlignedTopAppBar(title = {
                        Text(
                            text = if (globalSubGroupsDesc.value.isEmpty()) currentTopLevelDestination.titleTextId
                            else currentTopLevelDestination.titleTextId + " - " + globalSubGroupsDesc.value,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }, actions = {
                        Column {
                            IconButton(onClick = {
                                isDropDownMenuOpen.value = !isDropDownMenuOpen.value
                            }) {
                                Icon(
                                    imageVector = Icons.Outlined.List, contentDescription = null
                                )
                            }
                            DropdownMenuLayout(isDropDownMenuOpen)
                        }
                    })
                }
            }
            NavHost(
                navController = navController, startDestination = TopLevelDestination.Practice.route
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
            NavigationBarItem(selected = selected,
                onClick = { navigateToTopLevelDestination(navController, destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null
                    )
                },
                label = { Text(text = destination.iconTextId) })
        }
    }
}

@Composable
fun DropdownMenuLayout(isOpen: MutableState<Boolean>) {
    DropdownMenu(
        expanded = isOpen.value,
        onDismissRequest = { isOpen.value = !isOpen.value },
        modifier = Modifier.background(MaterialTheme.colorScheme.onPrimary)
    ) {
        val stepLength = 200
        for (i in globalGroups.indices step stepLength) {
            DropdownMenuItem(text = {
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "${i + 1}-${i + stepLength}", modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }, onClick = {
                getSubGroupsWithIndex(i, i + stepLength)
            })
        }
        DropdownMenuItem(text = {
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "errors", modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }, onClick = {
            getSubGroupsWithErrorState()
        })
        DropdownMenuItem(text = {
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "unpracticed", modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }, onClick = {
            getUnpracticedGroups()
        })
        DropdownMenuItem(text = {
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "all", modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }, onClick = {
            getAllGroups()
        })

    }
}
