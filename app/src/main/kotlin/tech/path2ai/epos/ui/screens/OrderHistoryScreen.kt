package tech.path2ai.epos.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tech.path2ai.epos.managers.OrderManager
import tech.path2ai.epos.models.*
import tech.path2ai.epos.terminal.*
import tech.path2ai.epos.ui.theme.OCGreen
import tech.path2ai.epos.ui.theme.OCRed
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderHistoryScreen(
    terminalManager: TerminalManager,
    orderManager: OrderManager,
    onBack: () -> Unit
) {
    val orders by orderManager.orders.collectAsState()
    var refundOrder by remember { mutableStateOf<CompletedOrder?>(null) }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.UK) }

    refundOrder?.let { order ->
        RefundPaymentScreen(
            order = order,
            terminalManager = terminalManager,
            orderManager = orderManager,
            onDismiss = { refundOrder = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (orders.isNotEmpty()) {
                        TextButton(onClick = { orderManager.clearHistory() }) {
                            Text("Clear All", color = OCRed)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (orders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(Modifier.height(8.dp))
                    Text("No orders yet", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).padding(horizontal = 16.dp)) {
                items(orders) { order ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(order.orderReference, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.width(8.dp))
                                    StatusBadge(order.status, order.orderType)
                                }
                                Text(dateFormat.format(Date(order.date)), fontSize = 12.sp, color = Color.Gray)
                                order.cardDisplay?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(order.formattedAmount, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                // Show the tip breakdown when this order carried one.
                                if (order.hasTip) {
                                    Text(
                                        "incl. ${order.formattedTip} tip",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            if (order.canRefund) {
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { refundOrder = order }) {
                                    Text("Refund", color = OCRed, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: OrderStatus, orderType: OrderType) {
    val (text, color) = when {
        orderType == OrderType.REFUND -> "REFUND" to Color(0xFF9C27B0)
        status == OrderStatus.COMPLETED -> "COMPLETED" to OCGreen
        status == OrderStatus.DECLINED -> "DECLINED" to OCRed
        status == OrderStatus.REFUNDED -> "REFUNDED" to Color(0xFFFF9800)
        else -> "" to Color.Gray
    }
    if (text.isNotEmpty()) {
        Surface(
            color = color.copy(alpha = 0.12f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
