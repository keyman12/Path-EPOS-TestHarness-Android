package tech.path2ai.epos.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.path2ai.epos.managers.OrderManager
import tech.path2ai.epos.terminal.TerminalConnectionState
import tech.path2ai.epos.terminal.TerminalManager
import tech.path2ai.epos.ui.theme.OCGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    terminalManager: TerminalManager,
    orderManager: OrderManager,
    onBack: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val connectionState by terminalManager.connectionState.collectAsState()
    val orders by orderManager.orders.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // Payment Terminal
            Text("Payment Terminal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text(terminalManager.adapterName) },
                supportingContent = { Text(terminalManager.connectionLabel) },
                leadingContent = {
                    Icon(
                        Icons.Default.CreditCard,
                        contentDescription = null,
                        tint = if (connectionState is TerminalConnectionState.Connected) OCGreen else MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clickable(onClick = onNavigateToTerminal)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Orders
            Text("Orders", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text("Order History") },
                supportingContent = { Text("${orders.size} orders") },
                leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onNavigateToHistory)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // About
            Text("About", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text("OrderChampion EPOS") },
                supportingContent = { Text("v1.0 · ${terminalManager.adapterName}") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
            )
        }
    }
}
