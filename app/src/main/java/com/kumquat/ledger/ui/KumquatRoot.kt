package com.kumquat.ledger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kumquat.ledger.ui.components.BottomGlassNav
import com.kumquat.ledger.ui.components.KumquatBackground
import com.kumquat.ledger.ui.components.NavItem
import com.kumquat.ledger.ui.nav.Routes
import com.kumquat.ledger.ui.screens.AboutScreen
import com.kumquat.ledger.ui.screens.AddTxScreen
import com.kumquat.ledger.ui.screens.AssetsScreen
import com.kumquat.ledger.ui.screens.BooksScreen
import com.kumquat.ledger.ui.screens.CalendarScreen
import com.kumquat.ledger.ui.screens.CategoriesScreen
import com.kumquat.ledger.ui.screens.DashboardScreen
import com.kumquat.ledger.ui.screens.DayListScreen
import com.kumquat.ledger.ui.screens.HuiWangDetailScreen
import com.kumquat.ledger.ui.screens.ImportExportScreen
import com.kumquat.ledger.ui.screens.MeScreen
import com.kumquat.ledger.ui.screens.StatsScreen

/**
 * 应用根：Y2K 渐变背景 + NavHost + 仅 4 个主页面显示悬浮底栏。
 *
 * v1.1.1 视觉：恢复 v1.1.0 的悬浮胶囊形态，白色底改为半透明（alpha 0.72），
 * 列表内容从底栏下方穿过，既保留通透感，又比 v1.1.0 的 0.55 更压得住文字。
 * 页面通过底部安全留白保证最后一项能完整滚出底栏。
 */
@Composable
fun KumquatRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showChrome = route == null || route in Routes.tabs
    val tabIndex = Routes.tabs.indexOf(route).let { if (it < 0) 0 else it }

    val navItems = listOf(
        NavItem("账单", Icons.Rounded.ReceiptLong),
        NavItem("日历", Icons.Rounded.CalendarMonth),
        NavItem("统计", Icons.Rounded.Leaderboard),
        NavItem("我的", Icons.Rounded.Person)
    )

    Box(Modifier.fillMaxSize()) {
        KumquatBackground {
            NavHost(
                navController = nav,
                startDestination = Routes.BILLS,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                composable(Routes.BILLS) {
                    DashboardScreen(
                        onAdd = { date -> nav.navigate(Routes.add(date = date)) },
                        onOpenTx = { id -> nav.navigate(Routes.add(txId = id)) },
                        onManageBooks = { nav.navigate(Routes.BOOKS) },
                        onOpenDay = { date -> nav.navigate(Routes.day(date)) },
                        onOpenImportExport = { nav.navigate(Routes.IMPORT_EXPORT) }
                    )
                }
                composable(Routes.CALENDAR) {
                    CalendarScreen(onOpenDay = { date -> nav.navigate(Routes.day(date)) })
                }
                composable(Routes.STATS) {
                    StatsScreen(
                        onOpenHuiWang = { start, end -> nav.navigate(Routes.huiwang(start, end)) }
                    )
                }
                composable(Routes.ME) {
                    MeScreen(
                        onBooks = { nav.navigate(Routes.BOOKS) },
                        onAssets = { nav.navigate(Routes.ASSETS) },
                        onCategories = { nav.navigate(Routes.CATEGORIES) },
                        onImportExport = { nav.navigate(Routes.IMPORT_EXPORT) },
                        onAbout = { nav.navigate(Routes.ABOUT) }
                    )
                }
                composable(
                    Routes.ADD,
                    arguments = listOf(
                        navArgument("txId") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("date") { type = NavType.StringType; defaultValue = "" }
                    )
                ) { backStack ->
                    AddTxScreen(
                        txId = backStack.arguments?.getLong("txId") ?: -1L,
                        presetDate = backStack.arguments?.getString("date").orEmpty(),
                        onDone = { nav.popBackStack() },
                        onBack = { nav.popBackStack() }
                    )
                }
                composable(
                    Routes.DAY,
                    arguments = listOf(navArgument("date") { type = NavType.StringType })
                ) { backStack ->
                    val date = backStack.arguments?.getString("date").orEmpty()
                    DayListScreen(
                        date = date,
                        onBack = { nav.popBackStack() },
                        onAdd = { d -> nav.navigate(Routes.add(date = d)) },
                        onOpenTx = { id -> nav.navigate(Routes.add(txId = id)) }
                    )
                }
                composable(
                    Routes.HUIWANG,
                    arguments = listOf(
                        navArgument("start") { type = NavType.StringType },
                        navArgument("end") { type = NavType.StringType }
                    )
                ) { backStack ->
                    HuiWangDetailScreen(
                        start = backStack.arguments?.getString("start").orEmpty(),
                        end = backStack.arguments?.getString("end").orEmpty(),
                        onBack = { nav.popBackStack() },
                        onOpenTx = { id -> nav.navigate(Routes.add(txId = id)) }
                    )
                }
                composable(Routes.BOOKS) { BooksScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.ASSETS) { AssetsScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.IMPORT_EXPORT) { ImportExportScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.CATEGORIES) { CategoriesScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
            }
        }

        if (showChrome) {
            BottomGlassNav(
                items = navItems,
                selectedIndex = tabIndex,
                onSelect = { index ->
                    val target = Routes.tabs[index]
                    nav.navigate(target) {
                        popUpTo(Routes.BILLS) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onAdd = { nav.navigate(Routes.add()) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}
