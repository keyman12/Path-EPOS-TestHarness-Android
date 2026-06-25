package tech.path2ai.epos.terminal

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simulated OCPay P400 terminal adapter for development/testing.
 * Returns deterministic approved responses without real hardware.
 */
class OCPayTerminalAdapter : PaymentTerminalAdapter {

    override val adapterName = "OCPay P400 (simulated)"

    private val _connectionState = MutableStateFlow<TerminalConnectionState>(TerminalConnectionState.Disconnected)
    override val connectionState: StateFlow<TerminalConnectionState> = _connectionState.asStateFlow()

    override suspend fun connect() {
        _connectionState.value = TerminalConnectionState.Connecting
        delay(1_400)
        _connectionState.value = TerminalConnectionState.Connected
    }

    override suspend fun disconnect() {
        _connectionState.value = TerminalConnectionState.Disconnected
    }

    override suspend fun scanForDevices(): List<TerminalDeviceInfo> = emptyList()
    override suspend fun connectToDevice(id: String) {}

    override suspend fun setIdleBranding(branding: CustomerDisplayBranding?) {
        // Loopback: no physical customer screen. Logged for parity with the
        // demo, where the Verifone adapter paints the logo on the VP100.
        android.util.Log.d(
            "OCPay",
            if (branding != null)
                "idle branding set (caption='${branding.caption ?: ""}', " +
                    "${branding.imageBytes?.size ?: 0}B logo) — no-op on the simulator"
            else "idle branding cleared — no-op on the simulator"
        )
    }

    override suspend fun submitSale(request: TerminalSaleRequest): TerminalSaleResponse {
        delay(2_500)

        // Loopback decline/timeout simulation (Settings → "Simulate next card
        // outcome"). A real payment SDK gets these from the acquirer; here we
        // fabricate them so the EPOS's non-approval handling is exercisable.
        when (request.simulatedOutcome) {
            SimulatedOutcome.DECLINE -> return TerminalSaleResponse(
                authorised = false, failureReason = "Card declined (simulated)"
            )
            SimulatedOutcome.NO_CARD -> return TerminalSaleResponse(
                authorised = false, timedOut = true,
                failureReason = "No card presented (simulated)"
            )
            SimulatedOutcome.WALK_AWAY -> return TerminalSaleResponse(
                authorised = false, customerTimedOut = true,
                failureReason = "Customer didn't respond (simulated)"
            )
            SimulatedOutcome.TERMINAL_ERROR -> return TerminalSaleResponse(
                authorised = false, notCompleted = true,
                failureReason = "Terminal couldn't complete the transaction (simulated)"
            )
            SimulatedOutcome.APPROVE -> { /* fall through to the normal approved sale */ }
        }

        val authCode = "%06d".format((100_000..999_999).random())
        val txnRef = "OCP-${(System.currentTimeMillis() / 1000) % 1_000_000}-${(1_000..9_999).random()}"
        val timestamp = java.time.Instant.now().toString()

        // Loopback tipping: when the EPOS asks for a customer tip prompt, we
        // simulate a customer who always picks one of the three presets at
        // random (10% / 15% / 20%). Tip is rounded up to the nearest penny.
        val base = request.amountPence
        val (tip, percentX10) = if (request.promptForTip) {
            val percent = listOf(100, 150, 200).random()
            val tipAmt = ((base.toLong() * percent + 999) / 1000).toInt()
            tipAmt to percent
        } else {
            0 to null
        }
        val total = base + tip

        return TerminalSaleResponse(
            authorised = true,
            authorisationCode = authCode,
            cardScheme = "VISA",
            maskedPan = "****1234",
            terminalReference = txnRef,
            cardReceiptData = TerminalCardReceipt(
                status = "APPROVED",
                timestamp = timestamp,
                txnRef = txnRef,
                terminalId = "OC-TILL-01",
                merchantId = "OC-MERCHANT-001",
                authorisationCode = authCode,
                verificationMethod = "CONTACTLESS",
                aid = "A0000000031010",
                entryMode = "CONTACTLESS",
                maskedPan = "****1234",
                cardScheme = "VISA"
            ),
            failureReason = null,
            baseAmountPence = base,
            tipAmountPence = tip,
            totalAmountPence = total,
            tipPercentX10 = percentX10
        )
    }

    override suspend fun submitRefund(request: TerminalRefundRequest): TerminalRefundResponse {
        delay(1_500)
        val refundRef = "OCP-REF-${(System.currentTimeMillis() / 1000) % 1_000_000}-${(1_000..9_999).random()}"
        return TerminalRefundResponse(
            succeeded = true,
            refundReference = refundRef,
            failureReason = null
        )
    }

    override suspend fun submitVoid(request: TerminalVoidRequest): TerminalVoidResponse {
        delay(1_500)
        val voidRef = "OCP-VOID-${(System.currentTimeMillis() / 1000) % 1_000_000}-${(1_000..9_999).random()}"
        return TerminalVoidResponse(
            succeeded = true,
            voidReference = voidRef,
            failureReason = null
        )
    }

    // ── Pre-authorization (tab hold) lifecycle ──────────────────────────────
    // All faked, like submitSale/Refund/Void above: canned OCP-PREAUTH-* refs,
    // always approved. A real payment SDK would round-trip to the acquirer.

    private fun preAuthRef() =
        "OCP-PREAUTH-${(System.currentTimeMillis() / 1000) % 1_000_000}-${(1_000..9_999).random()}"

    /** Place a hold (reserve funds, nothing debited). */
    override suspend fun submitPreAuth(request: TerminalPreAuthRequest): TerminalPreAuthResponse {
        delay(2_500)   // present-card delay, mirrors a real hold
        return TerminalPreAuthResponse(
            succeeded = true,
            terminalReference = preAuthRef(),
            holdAmountPence = request.amountPence
        )
    }

    /** Adjust a hold to a new total (re-uses the same handle). */
    override suspend fun submitAdjustPreAuth(request: TerminalPreAuthAdjustRequest): TerminalPreAuthResponse {
        delay(1_200)
        return TerminalPreAuthResponse(
            succeeded = true,
            terminalReference = request.originalTerminalReference,
            holdAmountPence = request.newTotalPence
        )
    }

    /** Capture (debit) the held amount and close the hold — carries a receipt. */
    override suspend fun submitCompletePreAuth(request: TerminalPreAuthCompleteRequest): TerminalPreAuthResponse {
        delay(1_500)
        val ref = "OCP-PREAUTH-CAP-${(System.currentTimeMillis() / 1000) % 1_000_000}-${(1_000..9_999).random()}"
        val authCode = "%06d".format((100_000..999_999).random())
        return TerminalPreAuthResponse(
            succeeded = true,
            terminalReference = ref,
            holdAmountPence = request.amountPence,
            cardReceiptData = TerminalCardReceipt(
                status = "APPROVED",
                timestamp = java.time.Instant.now().toString(),
                txnRef = ref,
                terminalId = "OC-TILL-01",
                merchantId = "OC-MERCHANT-001",
                authorisationCode = authCode,
                verificationMethod = "CONTACTLESS",
                aid = "A0000000031010",
                entryMode = "CONTACTLESS",
                maskedPan = "****1234",
                cardScheme = "VISA"
            )
        )
    }

    /** Release a hold without debiting. */
    override suspend fun submitVoidPreAuth(request: TerminalPreAuthVoidRequest): TerminalPreAuthResponse {
        delay(1_200)
        return TerminalPreAuthResponse(
            succeeded = true,
            terminalReference = "OCP-PREAUTH-VOID-${(System.currentTimeMillis() / 1000) % 1_000_000}-${(1_000..9_999).random()}"
        )
    }

    override suspend fun getTransactionStatus(reference: String): TerminalTransactionStatus {
        delay(500)
        return TerminalTransactionStatus(
            reference = reference,
            state = "APPROVED",
            amountPence = 0,
            timestamp = System.currentTimeMillis()
        )
    }
}
