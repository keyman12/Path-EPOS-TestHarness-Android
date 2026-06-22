package tech.path2ai.epos.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import tech.path2ai.epos.managers.InventoryManager
import tech.path2ai.epos.managers.OrderManager
import tech.path2ai.epos.terminal.TerminalManager

@Composable
fun AppNavigation(
    terminalManager: TerminalManager,
    orderManager: OrderManager,
    inventoryManager: InventoryManager
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "epos") {
        composable("epos") {
            EPOSScreen(
                terminalManager = terminalManager,
                orderManager = orderManager,
                inventoryManager = inventoryManager,
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                terminalManager = terminalManager,
                orderManager = orderManager,
                onBack = { navController.popBackStack() },
                onNavigateToTerminal = { navController.navigate("terminal_settings") },
                onNavigateToHistory = { navController.navigate("order_history") },
                onNavigateToSmtp = { navController.navigate("smtp_config") }
            )
        }
        composable("smtp_config") {
            SmtpConfigScreen(onBack = { navController.popBackStack() })
        }
        composable("terminal_settings") {
            TerminalSettingsScreen(
                terminalManager = terminalManager,
                onBack = { navController.popBackStack() }
            )
        }
        composable("order_history") {
            OrderHistoryScreen(
                terminalManager = terminalManager,
                orderManager = orderManager,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
