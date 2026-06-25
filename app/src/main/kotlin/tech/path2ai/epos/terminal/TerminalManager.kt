package tech.path2ai.epos.terminal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TerminalManager(private val adapter: PaymentTerminalAdapter) : ViewModel() {

    val connectionState: StateFlow<TerminalConnectionState> = adapter.connectionState

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<TerminalDeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<TerminalDeviceInfo>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun connect() {
        viewModelScope.launch {
            try { adapter.connect() } catch (_: Exception) { }
        }
    }

    fun disconnect() {
        viewModelScope.launch { adapter.disconnect() }
    }

    /**
     * Push the current customer-display branding (a merchant logo) to the
     * adapter — or clear it when disabled. Reads [CustomerDisplaySettings]; the
     * OCPay loopback treats it as a no-op. [context] is supplied by the caller
     * because this ViewModel holds no Context.
     */
    fun applyCustomerDisplayBranding(context: Context) {
        viewModelScope.launch {
            val branding = if (CustomerDisplaySettings.isEnabled(context)) {
                CustomerDisplayBranding(
                    imageBytes = CustomerDisplaySettings.logoBytes(context),
                    caption = CustomerDisplaySettings.caption(context).ifBlank { null }
                )
            } else null
            adapter.setIdleBranding(branding)
        }
    }

    fun scanForDevices() {
        viewModelScope.launch {
            _isScanning.value = true
            _discoveredDevices.value = emptyList()
            try {
                _discoveredDevices.value = adapter.scanForDevices()
            } catch (_: Exception) { }
            _isScanning.value = false
        }
    }

    fun connectToDevice(device: TerminalDeviceInfo) {
        viewModelScope.launch {
            try { adapter.connectToDevice(device.id) } catch (_: Exception) { }
        }
    }

    suspend fun submitSale(request: TerminalSaleRequest): TerminalSaleResponse {
        _isBusy.value = true
        return try {
            adapter.submitSale(request)
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun submitRefund(request: TerminalRefundRequest): TerminalRefundResponse {
        _isBusy.value = true
        return try {
            adapter.submitRefund(request)
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun submitVoid(request: TerminalVoidRequest): TerminalVoidResponse {
        _isBusy.value = true
        return try {
            adapter.submitVoid(request)
        } finally {
            _isBusy.value = false
        }
    }

    // ── Pre-authorization (tab hold) lifecycle ──────────────────────────────
    // Pass-throughs to the adapter, mirroring submitSale/Refund/Void with the
    // busy flag so the tab screens get the same in-flight signalling.

    suspend fun submitPreAuth(request: TerminalPreAuthRequest): TerminalPreAuthResponse {
        _isBusy.value = true
        return try { adapter.submitPreAuth(request) } finally { _isBusy.value = false }
    }

    suspend fun submitAdjustPreAuth(request: TerminalPreAuthAdjustRequest): TerminalPreAuthResponse {
        _isBusy.value = true
        return try { adapter.submitAdjustPreAuth(request) } finally { _isBusy.value = false }
    }

    suspend fun submitCompletePreAuth(request: TerminalPreAuthCompleteRequest): TerminalPreAuthResponse {
        _isBusy.value = true
        return try { adapter.submitCompletePreAuth(request) } finally { _isBusy.value = false }
    }

    suspend fun submitVoidPreAuth(request: TerminalPreAuthVoidRequest): TerminalPreAuthResponse {
        _isBusy.value = true
        return try { adapter.submitVoidPreAuth(request) } finally { _isBusy.value = false }
    }

    suspend fun getTransactionStatus(reference: String): TerminalTransactionStatus {
        return adapter.getTransactionStatus(reference)
    }

    /**
     * Abort an in-flight operation. The OCPay loopback has nothing to interrupt
     * (its calls just complete), so this only clears the busy flag — but the
     * payment screens still call it on cancel so the contract matches a real
     * terminal adapter that would need to tell the device to stop waiting.
     */
    fun cancelCurrentOperation() {
        _isBusy.value = false
    }

    val isConnected: Boolean
        get() = connectionState.value is TerminalConnectionState.Connected

    val adapterName: String get() = adapter.adapterName

    val connectionLabel: String
        get() = when (connectionState.value) {
            is TerminalConnectionState.Disconnected -> "Disconnected"
            is TerminalConnectionState.Connecting -> "Connecting…"
            is TerminalConnectionState.Connected -> "Connected"
            is TerminalConnectionState.Unavailable -> "Unavailable: ${(connectionState.value as TerminalConnectionState.Unavailable).reason}"
        }
}
