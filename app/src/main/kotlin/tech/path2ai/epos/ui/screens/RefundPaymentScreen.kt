package tech.path2ai.epos.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.first
import tech.path2ai.epos.managers.OrderManager
import tech.path2ai.epos.models.CompletedOrder
import tech.path2ai.epos.terminal.*
import tech.path2ai.epos.ui.theme.OCGreen

private enum class RefundState { PROCESSING, APPROVED, FAILED, ERROR }

@Composable
fun RefundPaymentScreen(
    order: CompletedOrder,
    terminalManager: TerminalManager,
    orderManager: OrderManager,
    onDismiss: () -> Unit
) {
    var state by remember { mutableStateOf(RefundState.PROCESSING) }
    var errorMessage by remember { mutableStateOf("") }
    var refundRef by remember { mutableStateOf<String?>(null) }
    val connectionState by terminalManager.connectionState.collectAsState()

    LaunchedEffect(Unit) {
        try {
            // Auto-connect if not connected
            if (connectionState !is TerminalConnectionState.Connected) {
                terminalManager.connect()
                // Wait for connected state
                terminalManager.connectionState.first { it is TerminalConnectionState.Connected }
            }

            val termRef = order.terminalReference
            if (termRef == null) {
                errorMessage = "No terminal reference for this order."
                state = RefundState.ERROR
                return@LaunchedEffect
            }

            val request = TerminalRefundRequest(
                originalTerminalReference = termRef,
                amountPence = order.amountPence,
                currencyCode = order.currencyCode,
                orderReference = orderManager.generateReference()
            )
            val response = terminalManager.submitRefund(request)
            if (response.succeeded) {
                refundRef = response.refundReference
                orderManager.markRefunded(order.id)
                orderManager.recordRefund(order, response.refundReference)
                state = RefundState.APPROVED
            } else {
                errorMessage = response.failureReason ?: "Refund declined"
                state = RefundState.FAILED
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "Terminal error"
            state = RefundState.ERROR
        }
    }

    Dialog(
        onDismissRequest = { if (state != RefundState.PROCESSING) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Card(modifier = Modifier.fillMaxWidth(0.45f).padding(16.dp)) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Refund", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("£%.2f".format(order.amountPence / 100.0), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))

                when (state) {
                    RefundState.PROCESSING -> {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Processing refund...\nPresent card on the terminal", textAlign = TextAlign.Center)
                    }
                    RefundState.APPROVED -> {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = OCGreen, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Refund Approved", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OCGreen)
                        refundRef?.let { Text("Ref: $it", color = Color.Gray, fontSize = 12.sp) }
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = OCGreen)) { Text("Done") }
                    }
                    RefundState.FAILED -> {
                        Icon(Icons.Default.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Refund Failed", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
                        Text(errorMessage, color = Color.Gray, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onDismiss) { Text("Close") }
                    }
                    RefundState.ERROR -> {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Error", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(errorMessage, color = Color.Gray, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onDismiss) { Text("Close") }
                    }
                }
            }
        }
    }
}
