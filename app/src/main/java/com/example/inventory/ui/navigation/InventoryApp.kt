package com.example.inventory.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.inventory.R
import com.example.inventory.ui.categoryedit.CategoryEditScreen
import com.example.inventory.ui.home.HomeScreen
import com.example.inventory.ui.itemdetail.ItemDetailScreen
import com.example.inventory.ui.itemedit.ItemEditScreen
import com.example.inventory.ui.itemlist.ItemListScreen
import com.example.inventory.ui.search.SearchScreen
import com.example.inventory.ui.settings.ArchivedCategoriesScreen
import com.example.inventory.ui.settings.SettingsScreen

/** Az alsó navigáció fülei (TERV.md 4. pont): Kategóriák · Keresés · Beállítások. */
private enum class TopLevelTab(
    val route: String,
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    CATEGORIES(Routes.HOME, R.string.nav_categories, Icons.Filled.GridView, Icons.Outlined.GridView),
    SEARCH(Routes.SEARCH, R.string.nav_search, Icons.Filled.Search, Icons.Filled.Search),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}

/** Az app gyökere: alsó navigáció + a képernyők. */
@Composable
fun InventoryApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // Az alsó sáv csak a fő füleken látszik (a később jövő részletes képernyőkön nem).
    val showBottomBar = TopLevelTab.entries.any { tab -> currentDestination?.route == tab.route }

    // A rendszer-sávokat a képernyők maguk kezelik (a fő fülek statusBarsPadding-gel, a többi saját Scaffolddal),
    // ezért a külső Scaffold nem ad hozzá insetet – különben a belső felső sáv kétszer kapná meg.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelTab.entries.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenSearch = {
                        navController.navigate(Routes.SEARCH) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenCategory = { id -> navController.navigate(Routes.itemList(id)) },
                    onNewCategory = { navController.navigate(Routes.categoryNew()) },
                )
            }
            composable(Routes.SEARCH) { SearchScreen(onOpenItem = { id -> navController.navigate(Routes.itemDetail(id)) }) }
            composable(Routes.SETTINGS) { SettingsScreen(onOpenArchived = { navController.navigate(Routes.ARCHIVED) }) }
            composable(
                Routes.CATEGORY_EDIT,
                arguments = listOf(navArgument(Routes.ARG_CATEGORY_ID) { type = NavType.LongType }),
            ) {
                CategoryEditScreen(onDone = { navController.popBackStack() })
            }
            composable(Routes.ARCHIVED) { ArchivedCategoriesScreen(onBack = { navController.popBackStack() }) }
            composable(
                Routes.ITEM_LIST,
                arguments = listOf(navArgument(Routes.ARG_CATEGORY_ID) { type = NavType.LongType }),
            ) {
                ItemListScreen(
                    onBack = { navController.popBackStack() },
                    onEditCategory = { id -> navController.navigate(Routes.categoryEdit(id)) },
                    onNewItem = { categoryId -> navController.navigate(Routes.itemNew(categoryId)) },
                    onOpenItem = { id -> navController.navigate(Routes.itemDetail(id)) },
                )
            }
            composable(
                Routes.ITEM_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_ITEM_ID) { type = NavType.LongType }),
            ) {
                ItemDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { itemId, categoryId -> navController.navigate(Routes.itemEdit(itemId, categoryId)) },
                )
            }
            composable(
                Routes.ITEM_EDIT,
                arguments = listOf(
                    navArgument(Routes.ARG_CATEGORY_ID) { type = NavType.LongType; defaultValue = 0L },
                    navArgument(Routes.ARG_ITEM_ID) { type = NavType.LongType; defaultValue = 0L },
                ),
            ) {
                ItemEditScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}
