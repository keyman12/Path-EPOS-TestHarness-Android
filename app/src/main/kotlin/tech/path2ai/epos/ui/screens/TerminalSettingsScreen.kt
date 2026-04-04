package tech.path2ai.epos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tech.path2ai.epos.terminal.TerminalConnectionState
import tech.path2ai.epos.terminal.TerminalManager
import tech.path2ai.epos.ui.theme.OCGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalSettingsScreen(
    terminalManager: TerminalManager,
    onBack: () -> Unit
) {
    val connectionState by terminalManager.connectionState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment Terminal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // Status card
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                when (connectionState) {
                                    is TerminalConnectionState.Connected -> OCGreen.copy(alpha = 0.15f)
                                    is TerminalConnectionState.Connecting -> Color(0xFFFF9800).copy(alpha = 0.15f)
                                    else -> Color(0xFFF44336).copy(alpha = 0.15f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (connectionState) {
                                is TerminalConnectionState.Connected -> Icons.Default.CheckCircle
                                is TerminalConnectionState.Connecting -> Icons.Default.Sync
                                else -> Icons.Default.Error
                            },
                            contentDescription = null,
                            tint = when (connectionState) {
                                is TerminalConnectionState.Connected -> OCGreen
                                is TerminalConnectionState.Connecting -> Color(0xFFFF9800)
                                else -> Color(0xFFF44336)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            when (connectionState) {
                                is TerminalConnectionState.Connected -> "Terminal Connected"
                                is TerminalConnectionState.Connecting -> "Connecting..."
                                else -> "No Terminal Connected"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            when (connectionState) {
                                is TerminalConnectionState.Connected -> "Ready to process payments"
                                is TerminalConnectionState.Connecting -> "Connecting to OCPay terminal..."
                                is TerminalConnectionState.Unavailable -> (connectionState as TerminalConnectionState.Unavailable).reason
                                else -> "Tap \"Connect\" to pair with the OCPay terminal"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    if (connectionState is TerminalConnectionState.Connecting) {
                        Spacer(Modifier.weight(1f))
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // OCPay Terminal section
            Text("OCPay Terminal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            if (connectionState is TerminalConnectionState.Connected) {
                // Disconnect button
                OutlinedButton(
                    onClick = { terminalManager.disconnect() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Disconnect")
                }
            } else {
                // Connect button
                Button(
                    onClick = { terminalManager.connect() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = connectionState !is TerminalConnectionState.Connecting,
                    colors = ButtonDefaults.buttonColors(containerColor = OCGreen)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Connect")
                    if (connectionState is TerminalConnectionState.Connecting) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // About
            Text("About", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text("Terminal") },
                trailingContent = { Text("OCPay P400 (simulated)", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
            )
            ListItem(
                headlineContent = { Text("Adapter") },
                trailingContent = { Text(terminalManager.adapterName, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
            )
        }
    }
}
