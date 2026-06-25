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

/**
 * Forces a deterministic outcome out of the OCPay loopback so the EPOS's
 * decline / timeout / error handling can be exercised without real hardware
 * (a real payment SDK ignores this — the outcome comes from the acquirer).
 * Driven from Settings → "Simulate next card outcome".
 */
enum class SimulatedOutcome { APPROVE, DECLINE, NO_CARD, WALK_AWAY, TERMINAL_ERROR }

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
    val promptForTip: Boolean = false,
    /** Loopback-only: which outcome to simulate. APPROVE = normal sale. */
    val simulatedOutcome: SimulatedOutcome = SimulatedOutcome.APPROVE
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
    val tipPercentX10: Int? = null,
    /**
     * True when the customer walked away from a terminal-facing prompt
     * (e.g. the tip screen) before choosing. Recoverable — the EPOS should
     * offer "Try again / Cancel" rather than a decline flow. OCPay loopback
     * never sets this; a real payment SDK adapter populates it when the
     * underlying SDK reports its customer-timeout state.
     */
    val customerTimedOut: Boolean = false,
    /** No card was presented within the window — NOT a decline. */
    val timedOut: Boolean = false,
    /** Terminal couldn't run the transaction (e.g. config issue) — NOT a decline. */
    val notCompleted: Boolean = false
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

data class TerminalVoidRequest(
    val originalTerminalReference: String,
    val orderReference: String
)

data class TerminalVoidResponse(
    val succeeded: Boolean,
    val voidReference: String? = null,
    val failureReason: String? = null
)

/**
 * Pre-authorization (a card hold): reserve funds without debiting, then later
 * adjust / complete (capture) / void (release) the hold. Used to back a bar/café
 * tab. The [TerminalPreAuthResponse.terminalReference] returned by the initial
 * hold is the handle the EPOS stores against the tab and replays for every
 * follow-on operation.
 *
 * Loopback-only: the OCPay adapter fakes the whole lifecycle with canned
 * `OCP-PREAUTH-*` references — exactly like it fakes sale / refund / void. A real
 * payment SDK integrated later would drive the terminal's own pre-auth flow.
 */
data class TerminalPreAuthRequest(
    val amountPence: Int,
    val currencyCode: String,
    val orderReference: String
)

data class TerminalPreAuthResponse(
    val succeeded: Boolean,
    /** Handle for adjust / complete / void; store this against the order/tab. */
    val terminalReference: String? = null,
    /** The current reserved/held total after this operation, in minor units. */
    val holdAmountPence: Int = 0,
    val failureReason: String? = null,
    /**
     * Card-receipt block for a CAPTURE (complete) — drives the settlement
     * receipt. Null for hold / adjust / void (nothing was debited). The OCPay
     * loopback fabricates it on completion, just as [submitSale] does for a sale.
     */
    val cardReceiptData: TerminalCardReceipt? = null
)

/** Adjust a hold to a NEW TOTAL (not a delta): higher = increase, lower = decrease. */
data class TerminalPreAuthAdjustRequest(
    val originalTerminalReference: String,
    val newTotalPence: Int,
    val currencyCode: String,
    val orderReference: String
)

/** Capture (debit) a held pre-auth and close it. [amountPence] may be <= the hold. */
data class TerminalPreAuthCompleteRequest(
    val originalTerminalReference: String,
    val amountPence: Int,
    val currencyCode: String,
    val orderReference: String
)

/** Release a held pre-auth without debiting. */
data class TerminalPreAuthVoidRequest(
    val originalTerminalReference: String,
    val orderReference: String
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
