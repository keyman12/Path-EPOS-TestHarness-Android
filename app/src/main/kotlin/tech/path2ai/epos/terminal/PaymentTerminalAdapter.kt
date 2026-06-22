package tech.path2ai.epos.terminal

import kotlinx.coroutines.flow.StateFlow

/**
 * Communication contract between the EPOS and a payment terminal.
 * Implementations handle device discovery, connection, and transactions.
 *
 * To replace the current integration:
 * 1. Create a new class implementing this interface
 * 2. Inject it into TerminalManager at the app entry point
 */
interface PaymentTerminalAdapter {
    val adapterName: String
    val connectionState: StateFlow<TerminalConnectionState>

    suspend fun connect()
    suspend fun disconnect()
    suspend fun scanForDevices(): List<TerminalDeviceInfo>
    suspend fun connectToDevice(id: String)
    suspend fun submitSale(request: TerminalSaleRequest): TerminalSaleResponse
    suspend fun submitRefund(request: TerminalRefundRequest): TerminalRefundResponse
    suspend fun submitVoid(request: TerminalVoidRequest): TerminalVoidResponse
    suspend fun getTransactionStatus(reference: String): TerminalTransactionStatus

    /**
     * Set (or clear) the idle customer-display branding (a merchant logo shown on
     * connect and between transactions — "attract mode"). Pass null to turn it
     * off. On a real Verifone terminal this paints the customer screen; the
     * OCPay loopback has no physical screen, so its implementation is a logged
     * no-op (present for parity with the demo).
     */
    suspend fun setIdleBranding(branding: CustomerDisplayBranding?)
}
