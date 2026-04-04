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
    val operatorId: String
)

data class TerminalSaleResponse(
    val authorised: Boolean,
    val authorisationCode: String? = null,
    val cardScheme: String? = null,
    val maskedPan: String? = null,
    val terminalReference: String? = null,
    val cardReceiptData: TerminalCardReceipt? = null,
    val failureReason: String? = null
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
