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
import kotlinx.coroutines.launch
import tech.path2ai.epos.managers.OrderManager
import tech.path2ai.epos.models.*
import tech.path2ai.epos.terminal.*
import tech.path2ai.epos.ui.theme.OCGreen

private enum class CardPaymentState {
    CHECKING_TERMINAL,
    WAITING_FOR_CARD,
    PROCESSING,
    APPROVED,
    DECLINED,
    ERROR
}

@Composable
fun CardPaymentScreen(
    cartItems: List<CartItem>,
    total: Int,
    terminalManager: TerminalManager,
    orderManager: OrderManager,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    var state by remember { mutableStateOf(CardPaymentState.CHECKING_TERMINAL) }
    var errorMessage by remember { mutableStateOf("") }
    var saleResponse by remember { mutableStateOf<TerminalSaleResponse?>(null) }
    val scope = rememberCoroutineScope()
    val connectionState by terminalManager.connectionState.collectAsState()

    LaunchedEffect(Unit) {
        try {
            // Auto-connect if not connected
            if (connectionState !is TerminalConnectionState.Connected) {
                state = CardPaymentState.CHECKING_TERMINAL
                terminalManager.connect()
                // Wait for connected state
                terminalManager.connectionState.first { it is TerminalConnectionState.Connected }
            }

            state = CardPaymentState.WAITING_FOR_CARD
            state = CardPaymentState.PROCESSING
            val request = TerminalSaleRequest(
                orderReference = orderManager.generateReference(),
                amountPence = total,
                currencyCode = "GBP",
                lineItems = cartItems.map {
                    TerminalLineItem(it.product.name, it.quantity, it.product.price)
                },
                operatorId = "till-01"
            )
            val response = terminalManager.submitSale(request)
            saleResponse = response

            if (response.authorised) {
                state = CardPaymentState.APPROVED
                orderManager.recordSale(
                    orderReference = request.orderReference,
                    lineItems = cartItems.map { OrderLineItem(it.product.name, it.quantity, it.product.price) },
                    amountPence = total,
                    currencyCode = "GBP",
                    paymentMethod = PaymentMethod.CARD,
                    cardLastFour = response.maskedPan?.takeLast(4),
                    cardScheme = response.cardScheme,
                    terminalReference = response.terminalReference,
                    authCode = response.authorisationCode
                )
            } else {
                errorMessage = response.failureReason ?: "Card declined"
                state = CardPaymentState.DECLINED
                orderManager.recordDeclinedSale(
                    orderReference = request.orderReference,
                    lineItems = cartItems.map { OrderLineItem(it.product.name, it.quantity, it.product.price) },
                    amountPence = total,
                    currencyCode = "GBP",
                    paymentMethod = PaymentMethod.CARD
                )
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "Terminal error"
            state = CardPaymentState.ERROR
        }
    }

    Dialog(
        onDismissRequest = { if (state == CardPaymentState.APPROVED || state == CardPaymentState.ERROR || state == CardPaymentState.DECLINED) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Card(modifier = Modifier.fillMaxWidth(0.45f).padding(16.dp)) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("£%.2f".format(total / 100.0), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))

                when (state) {
                    CardPaymentState.CHECKING_TERMINAL, CardPaymentState.WAITING_FOR_CARD -> {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Connecting to terminal...", textAlign = TextAlign.Center)
                    }
                    CardPaymentState.PROCESSING -> {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Processing payment...\nPresent card on the terminal", textAlign = TextAlign.Center)
                    }
                    CardPaymentState.APPROVED -> {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = OCGreen, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Payment Approved", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OCGreen)
                        saleResponse?.let { r ->
                            r.cardScheme?.let { Text("$it ${r.maskedPan ?: ""}", color = Color.Gray) }
                            r.authorisationCode?.let { Text("Auth: $it", color = Color.Gray, fontSize = 12.sp) }
                        }
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onComplete, colors = ButtonDefaults.buttonColors(containerColor = OCGreen)) {
                            Text("Done")
                        }
                    }
                    CardPaymentState.DECLINED -> {
                        Icon(Icons.Default.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Payment Declined", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
                        Text(errorMessage, color = Color.Gray, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onDismiss) { Text("Close") }
                    }
                    CardPaymentState.ERROR -> {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Terminal Error", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(errorMessage, color = Color.Gray, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onDismiss) { Text("Close") }
                    }
                }
            }
        }
    }
}
