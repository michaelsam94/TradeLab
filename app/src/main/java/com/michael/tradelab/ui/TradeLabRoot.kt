package com.michael.tradelab.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.michael.tradelab.R
import com.michael.tradelab.ui.alerts.AlertsScreen
import com.michael.tradelab.ui.disclaimer.DisclaimerGateScreen
import com.michael.tradelab.ui.history.HistoryScreen
import com.michael.tradelab.ui.indicators.IndicatorFeedScreen
import com.michael.tradelab.ui.learn.LearnScreen
import com.michael.tradelab.ui.markets.MarketsScreen
import com.michael.tradelab.ui.paywall.ProPaywallScreen
import com.michael.tradelab.ui.settings.SettingsScreen
import com.michael.tradelab.ui.trade.TradeScreen

object Routes {
    const val MARKETS = "markets"
    const val TRADE = "trade/{symbol}"
    const val INDICATORS = "indicators"
    const val HISTORY = "history"
    const val LEARN = "learn"
    const val ALERTS = "alerts"
    const val SETTINGS = "settings"
    const val PAYWALL = "paywall"

    fun trade(symbol: String) = "trade/$symbol"
}

private data class TopLevel(val route: String, val labelRes: Int, val icon: @Composable () -> Unit)

@Composable
fun TradeLabRoot(viewModel: MainViewModel = hiltViewModel()) {
    val accepted by viewModel.disclaimerAccepted.collectAsState()

    // Lifecycle-bound WebSocket: stream only while foregrounded; no foreground service.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, accepted) {
        val observer = LifecycleEventObserver { _, event ->
            if (accepted == true) {
                when (event) {
                    Lifecycle.Event.ON_START -> viewModel.onForeground()
                    Lifecycle.Event.ON_STOP -> viewModel.onBackground()
                    else -> Unit
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (accepted) {
        null -> Unit // splash still showing; avoid gate flicker
        false -> DisclaimerGateScreen()
        true -> MainScaffold(viewModel)
    }
}

@Composable
private fun MainScaffold(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val tabs = listOf(
        TopLevel(Routes.MARKETS, R.string.nav_markets) { Icon(Icons.Outlined.ShowChart, contentDescription = null) },
        TopLevel(Routes.trade("BTCUSDT"), R.string.nav_trade) { Icon(Icons.Outlined.CandlestickChart, contentDescription = null) },
        TopLevel(Routes.INDICATORS, R.string.nav_indicators) { Icon(Icons.Outlined.Insights, contentDescription = null) },
        TopLevel(Routes.HISTORY, R.string.nav_history) { Icon(Icons.Outlined.History, contentDescription = null) },
        TopLevel(Routes.LEARN, R.string.nav_learn) { Icon(Icons.Outlined.School, contentDescription = null) },
    )
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val baseRoute = tab.route.substringBefore("/")
                    val selected = currentRoute?.substringBefore("/") == baseRoute
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = tab.icon,
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.MARKETS,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.MARKETS) {
                MarketsScreen(onOpenPair = { navController.navigate(Routes.trade(it)) })
            }
            composable(
                Routes.TRADE,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "tradelab://pair/{symbol}" }),
            ) { entry ->
                TradeScreen(symbol = entry.arguments?.getString("symbol") ?: "BTCUSDT")
            }
            composable(
                Routes.INDICATORS,
                deepLinks = listOf(navDeepLink { uriPattern = "tradelab://indicators" }),
            ) {
                IndicatorFeedScreen(
                    onOpenPair = { navController.navigate(Routes.trade(it)) },
                    onOpenPaywall = { navController.navigate(Routes.PAYWALL) },
                )
            }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.LEARN) {
                LearnScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenAlerts = { navController.navigate(Routes.ALERTS) },
                )
            }
            composable(
                Routes.ALERTS,
                deepLinks = listOf(navDeepLink { uriPattern = "tradelab://alerts" }),
            ) { AlertsScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.PAYWALL) { ProPaywallScreen() }
        }
    }
}
