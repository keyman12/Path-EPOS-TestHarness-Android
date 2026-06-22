package tech.path2ai.epos.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.first
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
    /** Customer walked away from the tip prompt — offer Try Again / Cancel. */
    CUSTOMER_TIMEOUT,
    /** No card presented within the window — distinct from a decline. */
    TIMED_OUT,
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
    // Bumped when the cashier hits "Try Again" — re-keys the LaunchedEffect.
    var attemptToken by remember { mutableStateOf(0) }
    val connectionState by terminalManager.connectionState.collectAsState()
    val context = LocalContext.current

    // Closing mid-sale tells the terminal to cancel. The loopback OCPay adapter
    // has nothing to interrupt, but the contract matches a real terminal adapter
    // that would otherwise sit waiting for the card until its own timeout.
    val cancelAndDismiss = {
        if (state == CardPaymentState.CHECKING_TERMINAL ||
            state == CardPaymentState.WAITING_FOR_CARD ||
            state == CardPaymentState.PROCESSING
        ) {
            terminalManager.cancelCurrentOperation()
        }
        onDismiss()
    }

    LaunchedEffect(attemptToken) {
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
            val promptForTip = PaymentSettings.isTippingAllowed(context)
            val request = TerminalSaleRequest(
                orderReference = orderManager.generateReference(),
                amountPence = total,
                currencyCode = "GBP",
                lineItems = cartItems.map {
                    TerminalLineItem(it.product.name, it.quantity, it.product.price)
                },
                operatorId = "till-01",
                promptForTip = promptForTip,
                simulatedOutcome = PaymentSettings.simulatedOutcome(context)
            )
            val response = terminalManager.submitSale(request)
            saleResponse = response

            when {
                response.authorised -> {
                    state = CardPaymentState.APPROVED
                    val tip = response.tipAmountPence
                    val totalCharged = response.totalAmountPence.takeIf { it > 0 } ?: total
                    orderManager.recordSale(
                        orderReference = request.orderReference,
                        lineItems = cartItems.map { OrderLineItem(it.product.name, it.quantity, it.product.price) },
                        amountPence = totalCharged,
                        currencyCode = "GBP",
                        paymentMethod = PaymentMethod.CARD,
                        cardLastFour = response.maskedPan?.takeLast(4),
                        cardScheme = response.cardScheme,
                        terminalReference = response.terminalReference,
                        authCode = response.authorisationCode,
                        baseAmountPence = response.baseAmountPence.takeIf { it > 0 } ?: total,
                        tipAmountPence = tip.takeIf { it > 0 }
                    )
                }
                // Customer walked away from the tip prompt — recoverable, not a
                // decline. Don't record anything; let the cashier retry.
                response.customerTimedOut -> state = CardPaymentState.CUSTOMER_TIMEOUT
                // No card presented — NOT a decline. Don't record a declined sale.
                response.timedOut -> state = CardPaymentState.TIMED_OUT
                // Terminal couldn't run the transaction — not a decline either.
                response.notCompleted -> {
                    errorMessage = response.failureReason
                        ?: "The terminal couldn't complete this transaction."
                    state = CardPaymentState.ERROR
                }
                else -> {
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
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "Terminal error"
            state = CardPaymentState.ERROR
        }
    }

    Dialog(
        onDismissRequest = {
            if (state == CardPaymentState.APPROVED || state == CardPaymentState.ERROR ||
                state == CardPaymentState.DECLINED || state == CardPaymentState.TIMED_OUT
            ) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Card(modifier = Modifier.fillMaxWidth(0.45f).padding(16.dp)) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header figure: cart total while processing, card-charged total once we have a response.
                val displayAmount = saleResponse?.totalAmountPence?.takeIf { it > 0 } ?: total
                Text("£%.2f".format(displayAmount / 100.0), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))

                when (state) {
                    CardPaymentState.CHECKING_TERMINAL, CardPaymentState.WAITING_FOR_CARD -> {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Connecting to terminal...", textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = cancelAndDismiss) { Text("Cancel") }
                    }
                    CardPaymentState.PROCESSING -> {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Processing payment...\nPresent card on the terminal", textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = cancelAndDismiss) { Text("Cancel") }
                    }
                    CardPaymentState.APPROVED -> {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = OCGreen, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Payment Approved", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OCGreen)
                        saleResponse?.let { r ->
                            // Tip breakdown when the customer added a tip.
                            if (r.tipAmountPence > 0) {
                                Spacer(Modifier.height(8.dp))
                                val pct = r.tipPercentX10
                                val pctLabel = if (pct != null) {
                                    if (pct % 10 == 0) "${pct / 10}%"
                                    else "%.1f%%".format(pct / 10.0)
                                } else ""
                                Text(
                                    "Base £%.2f + Tip £%.2f".format(
                                        r.baseAmountPence / 100.0,
                                        r.tipAmountPence / 100.0
                                    ) + if (pctLabel.isNotEmpty()) " ($pctLabel)" else "",
                                    color = Color.Gray,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
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
                    CardPaymentState.CUSTOMER_TIMEOUT -> {
                        Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Customer Didn't Respond", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "The customer didn't pick a tip option in time. " +
                                "Try the sale again or cancel and return to the cart.",
                            color = Color.Gray, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { attemptToken += 1 }, colors = ButtonDefaults.buttonColors(containerColor = OCGreen)) {
                            Text("Try Again")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    }
                    CardPaymentState.TIMED_OUT -> {
                        Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No Card Presented", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "The card wasn't tapped in time. This is not a decline — try again or cancel.",
                            color = Color.Gray, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { attemptToken += 1 }, colors = ButtonDefaults.buttonColors(containerColor = OCGreen)) {
                            Text("Try Again")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onDismiss) { Text("Cancel") }
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
