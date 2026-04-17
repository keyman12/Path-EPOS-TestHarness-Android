package tech.path2ai.epos.terminal

sealed class TerminalConnectionState {
    data object Disconnected : TerminalConnectionState()
    data object Connecting : TerminalConnectionState()
    data object Connected : TerminalConnectionState()
    data class Unavailable(val reason: String) : TerminalConnectionState()
}

data class TerminalDeviceInfo(
    val id: String,
    val name: String
)

data class TerminalSaleRequest(
    val orderReference: String,
    val amountPence: Int,
    val currencyCode: String,
    val lineItems: List<TerminalLineItem>,
    val operatorId: String,
    /**
     * When true, the EPOS would like the card terminal to show a customer-
     * facing tip prompt before the card tap. The adapter decides how to
     * honour this — the OCPay loopback picks a random preset tip; a real
     * payment SDK would drive the terminal's own tip UI.
     */
    val promptForTip: Boolean = false
)

data class TerminalSaleResponse(
    val authorised: Boolean,
    val authorisationCode: String? = null,
    val cardScheme: String? = null,
    val maskedPan: String? = null,
    val terminalReference: String? = null,
    val cardReceiptData: TerminalCardReceipt? = null,
    val failureReason: String? = null,
    /** Base (pre-tip) amount in minor units. Defaults to the request amount. */
    val baseAmountPence: Int = 0,
    /** Customer-added tip in minor units. 0 when no tip (or no prompt). */
    val tipAmountPence: Int = 0,
    /** Card-charged total in minor units (= base + tip). */
    val totalAmountPence: Int = 0,
    /** Preset percentage × 10 (100 = 10%, 150 = 15%, 200 = 20%); null if not a preset. */
    val tipPercentX10: Int? = null
)

data class TerminalRefundRequest(
    val originalTerminalReference: String,
    val amountPence: Int,
    val currencyCode: String,
    val orderReference: String
)

data class TerminalRefundResponse(
    val succeeded: Boolean,
    val refundReference: String? = null,
    val failureReason: String? = null
)

data class TerminalTransactionStatus(
    val reference: String,
    val state: String,
    val amountPence: Int,
    val timestamp: Long
)

data class TerminalLineItem(
    val description: String,
    val quantity: Int,
    val unitAmountPence: Int
) {
    val totalAmountPence: Int get() = quantity * unitAmountPence
}

data class TerminalCardReceipt(
    val status: String,
    val timestamp: String,
    val txnRef: String,
    val terminalId: String,
    val merchantId: String,
    val authorisationCode: String,
    val verificationMethod: String,
    val aid: String,
    val entryMode: String,
    val maskedPan: String,
    val cardScheme: String
)

sealed class TerminalAdapterError(message: String) : Exception(message) {
    class DeviceNotAvailable(message: String) : TerminalAdapterError(message)
    class ConnectionFailed(message: String) : TerminalAdapterError(message)
    class TransactionFailed(message: String) : TerminalAdapterError(message)
    class InvalidRequest(message: String) : TerminalAdapterError(message)
}
