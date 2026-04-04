# Path EPOS Test Harness — Android

A working Android EPOS (coffee shop menu + cart + payments) with a **simulated** payment terminal adapter. The purpose of this harness is to validate that an AI coding agent can replace the simulated adapter with a real payment SDK integration end-to-end.

## Project Structure

```
app/src/main/kotlin/tech/path2ai/epos/
├── EPOSApplication.kt          — Application class
├── MainActivity.kt             — Entry point, wires TerminalManager
├── terminal/
│   ├── PaymentTerminalAdapter.kt   — Interface (DO NOT MODIFY)
│   ├── TerminalModels.kt           — Data classes (DO NOT MODIFY)
│   ├── TerminalManager.kt          — ViewModel wrapping the adapter
│   └── OCPayTerminalAdapter.kt     — Simulated adapter (REPLACE THIS)
├── managers/
│   ├── InventoryManager.kt         — Product catalogue
│   └── OrderManager.kt             — Order history, state
├── models/
│   ├── Cart.kt, Order.kt, Product.kt, Receipt.kt
└── ui/screens/
    ├── EPOSScreen.kt               — Product grid + cart
    ├── PaymentScreen.kt            — Payment method chooser
    ├── CardPaymentScreen.kt        — Card payment UI
    ├── CashPaymentScreen.kt        — Cash payment UI
    ├── OrderHistoryScreen.kt       — Completed orders
    ├── SettingsScreen.kt           — Settings + terminal status
    └── SplashScreen.kt             — Launch screen
```

## Integration Task — 4 Steps

Replace `OCPayTerminalAdapter` with a real Path Terminal SDK integration.

### Prerequisites

Add the Path Terminal SDK as a Gradle composite build dependency.

In `settings.gradle.kts`, add below the `include(":app")` line:

```kotlin
includeBuild("../path-terminal-sdk-android") {
    dependencySubstitution {
        substitute(module("tech.path2ai.sdk:path-core-models")).using(project(":path-core-models"))
        substitute(module("tech.path2ai.sdk:path-terminal-sdk")).using(project(":path-terminal-sdk"))
        substitute(module("tech.path2ai.sdk:path-emulator-adapter")).using(project(":path-emulator-adapter"))
        substitute(module("tech.path2ai.sdk:path-mock-adapter")).using(project(":path-mock-adapter"))
        substitute(module("tech.path2ai.sdk:path-diagnostics")).using(project(":path-diagnostics"))
    }
}
```

In `app/build.gradle.kts`, add to `dependencies {}`:

```kotlin
implementation("tech.path2ai.sdk:path-terminal-sdk:0.1.0")
implementation("tech.path2ai.sdk:path-emulator-adapter:0.1.0")
```

Add BLE permissions to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
```

### Step 1 — Disconnect the existing adapter

In `MainActivity.kt`, find where `OCPayTerminalAdapter` is instantiated and passed to `TerminalManager`. Comment it out. You will replace it in Step 3.

### Step 2 — Add BLE scan and connect

Create `PathTerminalAdapter.kt` implementing `PaymentTerminalAdapter`:

```kotlin
class PathTerminalAdapter(context: Context) : PaymentTerminalAdapter {

    private val bleAdapter = BLEPathTerminalAdapter(
        context = context,
        sdkVersion = "0.1.0",
        adapterVersion = "0.1.0"
    )
    private val terminal = PathTerminal(bleAdapter)

    override val adapterName = "Path Terminal (BLE)"

    private val _connectionState = MutableStateFlow<TerminalConnectionState>(
        TerminalConnectionState.Disconnected
    )
    override val connectionState: StateFlow<TerminalConnectionState> =
        _connectionState.asStateFlow()

    init {
        // Map SDK ConnectionState → TerminalConnectionState
        CoroutineScope(Dispatchers.Default).launch {
            terminal.events.collect { event ->
                when (event) {
                    is PathTerminalEvent.ConnectionStateChanged -> {
                        _connectionState.value = when (event.state) {
                            ConnectionState.Connected    -> TerminalConnectionState.Connected
                            ConnectionState.Connecting  -> TerminalConnectionState.Connecting
                            ConnectionState.Disconnected -> TerminalConnectionState.Disconnected
                            is ConnectionState.Failed   -> TerminalConnectionState.Unavailable(event.state.reason)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    override suspend fun connect() = terminal.connect()
    override suspend fun disconnect() = terminal.disconnect()

    override suspend fun scanForDevices(): List<TerminalDeviceInfo> =
        terminal.scanForDevices().map { TerminalDeviceInfo(it.id, it.name) }

    override suspend fun connectToDevice(id: String) = terminal.connectToDevice(id)
```

Key SDK imports:
```kotlin
import tech.path2ai.sdk.PathTerminal
import tech.path2ai.sdk.PathTerminalEvent
import tech.path2ai.sdk.ConnectionState
import tech.path2ai.sdk.emulator.BLEPathTerminalAdapter
```

### Step 3 — Wire sale and refund

Complete `PathTerminalAdapter` with sale and refund methods:

```kotlin
    override suspend fun submitSale(request: TerminalSaleRequest): TerminalSaleResponse {
        val sdkRequest = TransactionRequest(
            type = TransactionType.Sale,
            amount = request.amountPence,
            currency = request.currencyCode,
            reference = request.orderReference,
            operatorId = request.operatorId,
            lineItems = request.lineItems.map {
                tech.path2ai.sdk.core.LineItem(it.description, it.quantity, it.unitAmountPence)
            }
        )
        val result = terminal.submitTransaction(sdkRequest)
        return when {
            result.approved -> TerminalSaleResponse(
                authorised = true,
                authorisationCode = result.authorisationCode,
                cardScheme = result.cardScheme,
                maskedPan = result.maskedPan,
                terminalReference = result.transactionId,
                cardReceiptData = result.receiptData?.let { mapReceipt(it) }
            )
            else -> TerminalSaleResponse(
                authorised = false,
                failureReason = result.failureReason ?: "Declined"
            )
        }
    }

    override suspend fun submitRefund(request: TerminalRefundRequest): TerminalRefundResponse {
        val result = terminal.submitRefund(
            originalTransactionId = request.originalTerminalReference,
            amount = request.amountPence
        )
        return if (result.succeeded)
            TerminalRefundResponse(succeeded = true, refundReference = result.refundId)
        else
            TerminalRefundResponse(succeeded = false, failureReason = result.failureReason)
    }

    override suspend fun getTransactionStatus(reference: String): TerminalTransactionStatus {
        val status = terminal.getTransactionStatus(reference)
        return TerminalTransactionStatus(
            reference = reference,
            state = status.state.name,
            amountPence = status.amount,
            timestamp = status.timestamp
        )
    }

    private fun mapReceipt(r: tech.path2ai.sdk.core.ReceiptData): TerminalCardReceipt {
        val cr = r.customerReceipt
        return TerminalCardReceipt(
            status = cr.status, timestamp = cr.timestamp, txnRef = cr.txnRef,
            terminalId = cr.terminalId, merchantId = cr.merchantId,
            authorisationCode = cr.authorisationCode, verificationMethod = cr.verificationMethod,
            aid = cr.aid, entryMode = cr.entryMode, maskedPan = cr.maskedPan, cardScheme = cr.cardScheme
        )
    }
}
```

In `MainActivity.kt`, replace the `OCPayTerminalAdapter` with `PathTerminalAdapter(this)`.

### Step 4 — Verify end-to-end

Run the app on a device with Bluetooth. With the Path POS Emulator running:

1. **Settings → Payment Terminal → Scan** — the emulator should appear
2. **Connect** — status dot turns green
3. Add items to cart → **Pay Now → Card** — screen shows "Present card"
4. Present NFC tag to emulator — response comes back Approved
5. Receipt screen shows with EMV data (card scheme, masked PAN, auth code)
6. **Order History** — completed order visible with timestamp

## Key Interfaces

### PaymentTerminalAdapter (contract)

```kotlin
interface PaymentTerminalAdapter {
    val adapterName: String
    val connectionState: StateFlow<TerminalConnectionState>

    suspend fun connect()
    suspend fun disconnect()
    suspend fun scanForDevices(): List<TerminalDeviceInfo>
    suspend fun connectToDevice(id: String)
    suspend fun submitSale(request: TerminalSaleRequest): TerminalSaleResponse
    suspend fun submitRefund(request: TerminalRefundRequest): TerminalRefundResponse
    suspend fun getTransactionStatus(reference: String): TerminalTransactionStatus
}
```

### TerminalConnectionState (sealed class)

```kotlin
sealed class TerminalConnectionState {
    data object Disconnected : TerminalConnectionState()
    data object Connecting   : TerminalConnectionState()
    data object Connected    : TerminalConnectionState()
    data class Unavailable(val reason: String) : TerminalConnectionState()
}
```

## Build & Run

```bash
./gradlew assembleDebug
```

Install on device via Android Studio or `adb install`. Minimum SDK: API 26 (Android 8.0). Target: API 35.

## Reference Implementation

The fully integrated version is in the sibling repo `Path-epos-demo-sdk-android`. Compare `SDKTerminalManager.kt` and `AppTerminalManager.kt` there for a production-quality integration pattern.
