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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import tech.path2ai.epos.managers.OrderManager
import tech.path2ai.epos.terminal.PaymentSettings
import tech.path2ai.epos.terminal.SimulatedOutcome
import tech.path2ai.epos.terminal.TerminalConnectionState
import tech.path2ai.epos.terminal.TerminalManager
import tech.path2ai.epos.ui.theme.OCGreen

/** Friendly labels for the loopback decline-simulation selector. */
private fun SimulatedOutcome.label(): String = when (this) {
    SimulatedOutcome.APPROVE -> "Approve"
    SimulatedOutcome.DECLINE -> "Decline"
    SimulatedOutcome.NO_CARD -> "No card (timeout)"
    SimulatedOutcome.WALK_AWAY -> "Customer walk-away"
    SimulatedOutcome.TERMINAL_ERROR -> "Terminal error"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    terminalManager: TerminalManager,
    orderManager: OrderManager,
    onBack: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSmtp: () -> Unit
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

            // Payment Options — the OCPay loopback honours "Allow tipping" by
            // randomly picking one of 10% / 15% / 20% on each sale when on.
            Text("Payment Options", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            val context = LocalContext.current
            var allowTipping by remember {
                mutableStateOf(PaymentSettings.isTippingAllowed(context))
            }
            ListItem(
                headlineContent = { Text("Allow tipping") },
                supportingContent = {
                    Text(
                        "Asks the terminal to prompt the customer for a tip. " +
                            "In the OCPay loopback, picks a random preset (10/15/20%).",
                        color = Color.Gray
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Default.MonetizationOn,
                        contentDescription = null,
                        tint = if (allowTipping) OCGreen else Color.Gray
                    )
                },
                trailingContent = {
                    Switch(
                        checked = allowTipping,
                        onCheckedChange = {
                            allowTipping = it
                            PaymentSettings.setTippingAllowed(context, it)
                        }
                    )
                }
            )

            // Simulate next card outcome — loopback-only test control so the
            // decline / timeout / error handling can be exercised without
            // hardware. Sticky until changed; APPROVE = normal sale.
            var simOutcome by remember { mutableStateOf(PaymentSettings.simulatedOutcome(context)) }
            var simMenuOpen by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text("Simulate next card outcome") },
                supportingContent = {
                    Text(
                        "Forces the OCPay loopback's result so you can test declines, " +
                            "timeouts and terminal errors. Stays until you change it.",
                        color = Color.Gray
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Default.Science,
                        contentDescription = null,
                        tint = if (simOutcome == SimulatedOutcome.APPROVE) OCGreen else Color(0xFFFF9800)
                    )
                },
                trailingContent = {
                    Box {
                        TextButton(onClick = { simMenuOpen = true }) {
                            Text(simOutcome.label())
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = simMenuOpen, onDismissRequest = { simMenuOpen = false }) {
                            SimulatedOutcome.values().forEach { outcome ->
                                DropdownMenuItem(
                                    text = { Text(outcome.label()) },
                                    onClick = {
                                        simOutcome = outcome
                                        PaymentSettings.setSimulatedOutcome(context, outcome)
                                        simMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            )

            // Email receipts — configure the SMTP server used to email receipts.
            ListItem(
                headlineContent = { Text("Email receipts (SMTP)") },
                supportingContent = { Text("Configure the mail server used to send receipts", color = Color.Gray) },
                leadingContent = { Icon(Icons.Default.Email, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onNavigateToSmtp)
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

/** Sub-screen hosting the SMTP (email receipts) configuration form. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmtpConfigScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Email Receipts (SMTP)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        SMTPConfigContent(modifier = Modifier.padding(padding))
    }
}
