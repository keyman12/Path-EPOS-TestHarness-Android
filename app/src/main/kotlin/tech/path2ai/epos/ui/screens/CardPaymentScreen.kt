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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import tech.path2ai.epos.managers.OrderManager
import tech.path2ai.epos.models.*
import tech.path2ai.epos.terminal.*
import tech.path2ai.epos.ui.theme.OCGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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
    var fullReceipt by remember { mutableStateOf<FullReceipt?>(null) }
    var showReceipt by remember { mutableStateOf(false) }
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
                    // Build the receipt and show it after a short beat.
                    fullReceipt = buildReceipt(cartItems, total, request.orderReference, response)
                    delay(300)
                    showReceipt = true
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

    // Once approved and the receipt is ready, the receipt dialog takes over
    // (on-screen view + Print / Email / No receipt / Done).
    if (showReceipt) {
        val receipt = fullReceipt
        if (receipt != null) {
            ReceiptDialog(receipt = receipt, onNoReceipt = onComplete, onDone = onComplete)
            return
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
                        // Brief beat before the receipt dialog takes over.
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = OCGreen, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Payment Approved", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OCGreen)
                        saleResponse?.let { r ->
                            r.cardScheme?.let { Text("$it ${r.maskedPan ?: ""}", color = Color.Gray) }
                            r.authorisationCode?.let { Text("Auth: $it", color = Color.Gray, fontSize = 12.sp) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Loading receipt…", color = Color.Gray, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = OCGreen, strokeWidth = 2.dp)
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

/** Build a [FullReceipt] from the cart + sale response data. */
private fun buildReceipt(
    cartItems: List<CartItem>,
    total: Int,
    orderRef: String,
    response: TerminalSaleResponse
): FullReceipt {
    // Use the breakdown from the terminal when present; fall back to the cart
    // total. Base amount drives the VAT calc (tip sits on top, not VATable);
    // total drives the bottom line so the receipt matches what was charged.
    val baseFromTerminal = response.baseAmountPence.takeIf { it > 0 }
    val totalFromTerminal = response.totalAmountPence.takeIf { it > 0 } ?: total
    val tip = response.tipAmountPence

    val cartBase = baseFromTerminal ?: total
    val subtotal = (cartBase / 1.2).roundToInt()
    val vatAmount = cartBase - subtotal

    val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.UK)

    return FullReceipt(
        merchantName = "Path Café",
        merchantAddress = "1 Tech Street, London EC1A 1BB",
        orderNumber = orderRef,
        tillNumber = "01",
        cashierName = "Cashier",
        orderDate = dateFormat.format(Date()),
        lineItems = cartItems.map {
            ReceiptLineItem(it.product.name, it.quantity, it.product.price)
        },
        subtotal = subtotal,
        vatAmount = vatAmount,
        total = totalFromTerminal,
        currency = "GBP",
        cardReceiptBlock = response.cardReceiptData?.let { CardReceiptBlock.from(it) },
        tipAmount = tip
    )
}
