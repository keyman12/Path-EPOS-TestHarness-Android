package tech.path2ai.epos.managers

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import tech.path2ai.epos.models.*

class OrderManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefsKey = "completed_orders"

    private val _orders = MutableStateFlow<List<CompletedOrder>>(emptyList())
    val orders: StateFlow<List<CompletedOrder>> = _orders.asStateFlow()

    init { loadOrders() }

    fun recordSale(
        orderReference: String,
        lineItems: List<OrderLineItem>,
        amountPence: Int,
        currencyCode: String,
        paymentMethod: PaymentMethod,
        cardLastFour: String? = null,
        cardScheme: String? = null,
        terminalReference: String? = null,
        authCode: String? = null,
        baseAmountPence: Int? = null,
        tipAmountPence: Int? = null,
        tabName: String? = null,
        settlementKind: String? = null
    ) {
        val order = CompletedOrder(
            orderReference = orderReference,
            lineItems = lineItems,
            amountPence = amountPence,
            currencyCode = currencyCode,
            paymentMethod = paymentMethod,
            cardLastFour = cardLastFour,
            cardScheme = cardScheme,
            terminalReference = terminalReference,
            authCode = authCode,
            status = OrderStatus.COMPLETED,
            baseAmountPence = baseAmountPence,
            tipAmountPence = tipAmountPence,
            tabName = tabName,
            settlementKind = settlementKind
        )
        _orders.value = listOf(order) + _orders.value
        saveOrders()
    }

    fun recordDeclinedSale(
        orderReference: String,
        lineItems: List<OrderLineItem>,
        amountPence: Int,
        currencyCode: String,
        paymentMethod: PaymentMethod
    ) {
        val order = CompletedOrder(
            orderReference = orderReference,
            lineItems = lineItems,
            amountPence = amountPence,
            currencyCode = currencyCode,
            paymentMethod = paymentMethod,
            status = OrderStatus.DECLINED
        )
        _orders.value = listOf(order) + _orders.value
        saveOrders()
    }

    fun markRefunded(orderId: String) {
        _orders.value = _orders.value.map {
            if (it.id == orderId) it.copy(status = OrderStatus.REFUNDED, refundedAt = System.currentTimeMillis())
            else it
        }
        saveOrders()
    }

    fun markVoided(orderId: String) {
        _orders.value = _orders.value.map {
            if (it.id == orderId) it.copy(status = OrderStatus.VOIDED, voidedAt = System.currentTimeMillis())
            else it
        }
        saveOrders()
    }

    fun recordVoid(originalOrder: CompletedOrder, voidReference: String?) {
        val void = CompletedOrder(
            orderReference = generateReference(),
            lineItems = originalOrder.lineItems,
            amountPence = originalOrder.amountPence,
            currencyCode = originalOrder.currencyCode,
            paymentMethod = originalOrder.paymentMethod,
            orderType = OrderType.VOID,
            terminalReference = voidReference,
            status = OrderStatus.COMPLETED
        )
        _orders.value = listOf(void) + _orders.value
        saveOrders()
    }

    /**
     * Record a whole-line item refund: log a REFUND order for the selected lines
     * (plus the tip on a clearing refund), and mark those line indices refunded on
     * the original sale. When every line has been refunded the sale flips to
     * REFUNDED; until then it stays COMPLETED and partially refundable.
     */
    fun recordItemRefund(
        originalOrder: CompletedOrder,
        refundReference: String?,
        refundedIndices: List<Int>,
        tipRefundPence: Int = 0
    ) {
        val items = refundedIndices.mapNotNull { originalOrder.lineItems.getOrNull(it) }
        val itemsValue = items.sumOf { it.lineTotal }
        val refund = CompletedOrder(
            orderReference = generateReference(),
            lineItems = items,
            amountPence = itemsValue + tipRefundPence,
            currencyCode = originalOrder.currencyCode,
            paymentMethod = originalOrder.paymentMethod,
            orderType = OrderType.REFUND,
            terminalReference = refundReference,
            status = OrderStatus.COMPLETED,
            tipAmountPence = tipRefundPence.takeIf { it > 0 }
        )
        val nowRefunded = (originalOrder.refundedLineIndices + refundedIndices).distinct().sorted()
        _orders.value = listOf(refund) + _orders.value.map { o ->
            if (o.id == originalOrder.id) {
                val fullyRefunded = nowRefunded.size >= o.lineItems.size
                o.copy(
                    refundedLineIndices = nowRefunded,
                    status = if (fullyRefunded) OrderStatus.REFUNDED else o.status,
                    refundedAt = if (fullyRefunded) System.currentTimeMillis() else o.refundedAt
                )
            } else o
        }
        saveOrders()
    }

    fun recordRefund(originalOrder: CompletedOrder, refundReference: String?) {
        val refund = CompletedOrder(
            orderReference = generateReference(),
            lineItems = originalOrder.lineItems,
            amountPence = originalOrder.amountPence,
            currencyCode = originalOrder.currencyCode,
            paymentMethod = originalOrder.paymentMethod,
            orderType = OrderType.REFUND,
            terminalReference = refundReference,
            status = OrderStatus.COMPLETED
        )
        _orders.value = listOf(refund) + _orders.value
        saveOrders()
    }

    fun clearHistory() {
        _orders.value = emptyList()
        saveOrders()
    }

    fun generateReference(): String = "OC-${(10000..99999).random()}"

    private fun saveOrders() {
        val prefs = context.getSharedPreferences("epos", Context.MODE_PRIVATE)
        prefs.edit().putString(prefsKey, json.encodeToString(_orders.value)).apply()
    }

    private fun loadOrders() {
        val prefs = context.getSharedPreferences("epos", Context.MODE_PRIVATE)
        val data = prefs.getString(prefsKey, null) ?: return
        try {
            _orders.value = json.decodeFromString<List<CompletedOrder>>(data)
        } catch (_: Exception) { }
    }
}
