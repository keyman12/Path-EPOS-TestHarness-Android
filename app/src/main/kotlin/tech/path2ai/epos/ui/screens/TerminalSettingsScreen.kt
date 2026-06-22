package tech.path2ai.epos.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import tech.path2ai.epos.terminal.CustomerDisplaySettings
import tech.path2ai.epos.terminal.PaymentSettings
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

            // ── Terminal login (connect-time session credentials) ───────────
            // The OCPay loopback doesn't authenticate, but the fields mirror the
            // demo's connect-time login; locked while connected (disconnect to edit).
            val context = LocalContext.current
            val connected = connectionState is TerminalConnectionState.Connected
            var loginUsername by remember { mutableStateOf(PaymentSettings.loginUsername(context)) }
            var loginPassword by remember { mutableStateOf(PaymentSettings.loginPassword(context)) }
            var loginShift by remember { mutableStateOf(PaymentSettings.loginShift(context)) }
            var refundPassword by remember { mutableStateOf(PaymentSettings.refundPassword(context)) }
            var passwordVisible by remember { mutableStateOf(false) }

            Text("Terminal login", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = loginUsername,
                onValueChange = { loginUsername = it; PaymentSettings.setLoginUsername(context, it) },
                label = { Text("Login username") },
                singleLine = true,
                enabled = !connected,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = loginPassword,
                onValueChange = { loginPassword = it; PaymentSettings.setLoginPassword(context, it) },
                label = { Text("Login password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                enabled = !connected,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = loginShift,
                onValueChange = { loginShift = it; PaymentSettings.setLoginShift(context, it) },
                label = { Text("Login shift") },
                singleLine = true,
                enabled = !connected,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = refundPassword,
                onValueChange = { refundPassword = it; PaymentSettings.setRefundPassword(context, it) },
                label = { Text("Refund password (blank on test estates)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // ── Customer Display (attract-mode merchant logo) ────────────────
            // Loopback parity: the upload / resize / toggle settings mirror the
            // demo's; the OCPay simulator has no real customer screen, so applying
            // is a logged no-op. On connect we still drive it for flow parity.
            LaunchedEffect(connectionState) {
                if (connectionState is TerminalConnectionState.Connected) {
                    terminalManager.applyCustomerDisplayBranding(context)
                }
            }
            Text("Customer Display", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            var brandingEnabled by remember { mutableStateOf(CustomerDisplaySettings.isEnabled(context)) }
            var logoCaption by remember { mutableStateOf(CustomerDisplaySettings.caption(context)) }
            var logoVersion by remember { mutableStateOf(0) }
            val logoBitmap = remember(logoVersion) {
                val bytes = CustomerDisplaySettings.logoBytes(context)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
            val hasCustomLogo = remember(logoVersion) { CustomerDisplaySettings.hasCustomLogo(context) }
            val logoPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    val src = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (src != null && CustomerDisplaySettings.saveLogo(context, src) != null) {
                        logoVersion++
                        if (connected) terminalManager.applyCustomerDisplayBranding(context)
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show merchant logo", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Shows your logo on the terminal's customer screen on connect, and " +
                                    "again after every payment (attract mode). Simulated here — the " +
                                    "loopback terminal has no physical screen.",
                                style = MaterialTheme.typography.bodySmall, color = Color.Gray
                            )
                        }
                        Switch(
                            checked = brandingEnabled,
                            onCheckedChange = {
                                brandingEnabled = it
                                CustomerDisplaySettings.setEnabled(context, it)
                                if (connected) terminalManager.applyCustomerDisplayBranding(context)
                            }
                        )
                    }
                    if (brandingEnabled) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF2F2F2)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (logoBitmap != null) {
                                Image(
                                    bitmap = logoBitmap,
                                    contentDescription = "Customer display logo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.padding(12.dp).fillMaxSize()
                                )
                            } else {
                                Text("No image", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row {
                            OutlinedButton(
                                onClick = { logoPicker.launch("image/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Choose logo")
                            }
                            if (hasCustomLogo) {
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        CustomerDisplaySettings.resetLogo(context)
                                        logoVersion++
                                        if (connected) terminalManager.applyCustomerDisplayBranding(context)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Reset to default") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = logoCaption,
                            onValueChange = {
                                logoCaption = it
                                CustomerDisplaySettings.setCaption(context, it)
                            },
                            label = { Text("Caption shown under the logo (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { terminalManager.applyCustomerDisplayBranding(context) },
                            enabled = connected,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Apply to terminal now")
                        }
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
