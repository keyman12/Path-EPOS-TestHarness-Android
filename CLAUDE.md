# Path EPOS Test Harness — Android

A working Android EPOS (coffee shop menu + cart + payments) with a **simulated** payment terminal adapter. The purpose of this harness is to validate that an AI coding agent can replace the simulated adapter with a real payment SDK integration end-to-end.

## Project Structure

```
app/src/main/kotlin/tech/path2ai/epos/
├── EPOSApplication.kt
├── MainActivity.kt                      — Entry point, wires TerminalManager
├── terminal/
│   ├── PaymentTerminalAdapter.kt        — Interface (DO NOT MODIFY)
│   ├── TerminalModels.kt                — Data classes (MODIFY in Step 4)
│   ├── TerminalManager.kt               — ViewModel wrapping the adapter (DO NOT MODIFY)
│   └── OCPayTerminalAdapter.kt          — Simulated adapter (REPLACE THIS)
├── managers/
│   ├── InventoryManager.kt
│   └── OrderManager.kt                  — MODIFY in Step 4
├── models/
│   └── Cart.kt, Order.kt, Product.kt, Receipt.kt  — Order.kt MODIFY in Step 4
└── ui/screens/
    ├── EPOSScreen.kt
    ├── PaymentScreen.kt
    ├── CardPaymentScreen.kt             — MODIFY in Step 5
    ├── CashPaymentScreen.kt
    ├── RefundPaymentScreen.kt           — MODIFY in Step 5
    ├── OrderHistoryScreen.kt            — MODIFY in Step 6
    ├── SettingsScreen.kt
    ├── TerminalSettingsScreen.kt        — REPLACE in Step 3
    └── SplashScreen.kt
```

## Integration Task — 6 Steps

Replace `OCPayTerminalAdapter` with a real Path Terminal SDK integration, including full receipt display after sales and refunds.

---

### Prerequisites — SDK dependency

`path-terminal-init` has already added JitPack and the SDK dependency lines to your Gradle files. Verify `settings.gradle.kts` contains:

```kotlin
maven { url = uri("https://jitpack.io") }
```

And `app/build.gradle.kts` contains (uncommented):

```kotlin
implementation("com.github.keyman12.path-terminal-sdk-android:path-core-models:v1.3")
implementation("com.github.keyman12.path-terminal-sdk-android:path-terminal-sdk:v1.3")
implementation("com.github.keyman12.path-terminal-sdk-android:path-emulator-adapter:v1.3")
```

If these lines are still commented out, uncomment them now.

Add BLE permissions to `AndroidManifest.xml` inside `<manifest>` before `<application>`:

```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
```

---

### Step 1 — Create PathTerminalAdapter.kt

Create `app/src/main/kotlin/tech/path2ai/epos/terminal/PathTerminalAdapter.kt` with this exact content:

```kotlin
package tech.path2ai.epos.terminal

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import tech.path2ai.sdk.ConnectionState
import tech.path2ai.sdk.PathTerminal
import tech.path2ai.sdk.PathTerminalEvent
import tech.path2ai.sdk.core.DiscoveredDevice
import tech.path2ai.sdk.core.RequestEnvelope
import tech.path2ai.sdk.core.TransactionRequest
import tech.path2ai.sdk.emulator.BLEPathTerminalAdapter

private const val SDK_VERSION = "0.1.0"
private const val ADAPTER_VERSION = "0.1.0"

class PathTerminalAdapter(context: Context) : PaymentTerminalAdapter {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val bleAdapter = BLEPathTerminalAdapter(
        context = context,
        sdkVersion = SDK_VERSION,
        adapterVersion = ADAPTER_VERSION
    )
    private val terminal = PathTerminal(bleAdapter)

    private var discoveredDeviceCache: List<DiscoveredDevice> = emptyList()
    private val connectMutex = Mutex()

    override val adapterName = "Path Terminal (BLE)"

    private val _connectionState = MutableStateFlow<TerminalConnectionState>(
        TerminalConnectionState.Disconnected
    )
    override val connectionState: StateFlow<TerminalConnectionState> = _connectionState.asStateFlow()

    init {
        scope.launch {
            terminal.events.collect { event ->
                when (event) {
                    is PathTerminalEvent.ConnectionStateChanged -> {
                        _connectionState.value = mapConnectionState(event.state)
                    }
                    is PathTerminalEvent.Error -> {
                        if (_connectionState.value is TerminalConnectionState.Connecting) {
                            _connectionState.value = TerminalConnectionState.Unavailable(event.error.message)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun mapConnectionState(state: ConnectionState): TerminalConnectionState = when (state) {
        ConnectionState.Connected    -> TerminalConnectionState.Connected
        ConnectionState.Connecting  -> TerminalConnectionState.Connecting
        ConnectionState.Disconnected -> TerminalConnectionState.Disconnected
        is ConnectionState.Error    -> TerminalConnectionState.Unavailable(state.message)
        ConnectionState.Idle        -> if (terminal.isConnected) TerminalConnectionState.Connected else TerminalConnectionState.Disconnected
        ConnectionState.Scanning    -> TerminalConnectionState.Connecting
    }

    override suspend fun connect() {}

    override suspend fun disconnect() = terminal.disconnect()

    override suspend fun scanForDevices(): List<TerminalDeviceInfo> {
        val devices = terminal.discoverDevices()
        discoveredDeviceCache = devices
        return devices.map { TerminalDeviceInfo(it.id, it.name) }
    }

    override suspend fun connectToDevice(id: String) {
        if (!connectMutex.tryLock()) return
        try {
            val device = discoveredDeviceCache.find { it.id == id }
                ?: DiscoveredDevice(id = id, name = "Path Terminal", rssi = 0)
            try {
                terminal.connect(device)
            } catch (e: Exception) {
                // Android BLE commonly fails on the first GATT attempt — wait and retry once
                delay(1500L)
                terminal.connect(device)
            }
        } finally {
            connectMutex.unlock()
        }
    }

    override suspend fun submitSale(request: TerminalSaleRequest): TerminalSaleResponse {
        val envelope = RequestEnvelope.create(
            merchantReference = request.orderReference,
            sdkVersion = SDK_VERSION,
            adapterVersion = ADAPTER_VERSION
        )
        val sdkRequest = TransactionRequest.sale(
            amountMinor = request.amountPence,
            currency = request.currencyCode,
            envelope = envelope
        )
        val result = terminal.sale(sdkRequest)
        val receipt = if (result.isApproved && result.transactionId != null && result.receiptAvailable) {
            try { terminal.getReceiptData(result.transactionId!!) } catch (_: Exception) { null }
        } else null
        val cr = receipt?.customerReceipt
        return TerminalSaleResponse(
            authorised = result.isApproved,
            authorisationCode = cr?.authCode,
            cardScheme = cr?.cardScheme,
            maskedPan = cr?.maskedPan ?: result.cardLastFour?.let { "****$it" },
            terminalReference = result.transactionId ?: result.requestId,
            cardReceiptData = cr?.let {
                TerminalCardReceipt(
                    status = it.status,
                    timestamp = it.timestamp,
                    txnRef = it.txnRef,
                    terminalId = it.terminalId,
                    merchantId = it.merchantId,
                    authorisationCode = it.authCode,
                    verificationMethod = it.verification,
                    aid = it.aid,
                    entryMode = it.entryMode,
                    maskedPan = it.maskedPan,
                    cardScheme = it.cardScheme
                )
            },
            failureReason = if (!result.isApproved) result.error?.message ?: result.state.name else null
        )
    }

    override suspend fun submitRefund(request: TerminalRefundRequest): TerminalRefundResponse {
        val envelope = RequestEnvelope.create(
            merchantReference = request.orderReference,
            sdkVersion = SDK_VERSION,
            adapterVersion = ADAPTER_VERSION
        )
        val sdkRequest = TransactionRequest.refund(
            amountMinor = request.amountPence,
            currency = request.currencyCode,
            originalTransactionId = request.originalTerminalReference,
            envelope = envelope
        )
        val result = terminal.refund(sdkRequest)
        val receipt = if (result.isApproved && result.transactionId != null && result.receiptAvailable) {
            try { terminal.getReceiptData(result.transactionId!!) } catch (_: Exception) { null }
        } else null
        val cr = receipt?.customerReceipt
        return TerminalRefundResponse(
            succeeded = result.isApproved,
            refundReference = result.transactionId ?: result.requestId,
            cardReceiptData = cr?.let {
                TerminalCardReceipt(
                    status = it.status,
                    timestamp = it.timestamp,
                    txnRef = it.txnRef,
                    terminalId = it.terminalId,
                    merchantId = it.merchantId,
                    authorisationCode = it.authCode,
                    verificationMethod = it.verification,
                    aid = it.aid,
                    entryMode = it.entryMode,
                    maskedPan = it.maskedPan,
                    cardScheme = it.cardScheme
                )
            },
            failureReason = if (!result.isApproved) result.error?.message ?: result.state.name else null
        )
    }

    override suspend fun getTransactionStatus(reference: String): TerminalTransactionStatus {
        val result = terminal.getTransactionStatus(reference)
        val timestamp = try {
            java.time.Instant.parse(result.timestampUtc).toEpochMilli()
        } catch (_: Exception) { System.currentTimeMillis() }
        return TerminalTransactionStatus(
            reference = reference,
            state = result.state.name,
            amountPence = result.amountMinor,
            timestamp = timestamp
        )
    }
}
```

---

### Step 2 — Wire into MainActivity.kt

Replace the entire content of `MainActivity.kt`:

```kotlin
package tech.path2ai.epos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import tech.path2ai.epos.managers.InventoryManager
import tech.path2ai.epos.managers.OrderManager
import tech.path2ai.epos.terminal.PathTerminalAdapter
import tech.path2ai.epos.terminal.TerminalManager
import tech.path2ai.epos.ui.screens.SplashScreen
import tech.path2ai.epos.ui.theme.OrderchampionEPOSTheme

class MainActivity : ComponentActivity() {

    private val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        emptyArray()
    }

    private var bleGranted by mutableStateOf(false)

    private val requestBlePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        bleGranted = results.values.all { it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bleGranted = blePermissions.isEmpty() || blePermissions.all { perm ->
            ActivityCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }

        if (!bleGranted) {
            requestBlePermissions.launch(blePermissions)
        }

        val terminalManager = TerminalManager(PathTerminalAdapter(this))
        val orderManager = OrderManager(applicationContext)
        val inventoryManager = InventoryManager()

        setContent {
            OrderchampionEPOSTheme {
                if (bleGranted) {
                    SplashScreen(
                        terminalManager = terminalManager,
                        orderManager = orderManager,
                        inventoryManager = inventoryManager
                    )
                } else {
                    BluetoothPermissionScreen(
                        onRequest = { requestBlePermissions.launch(blePermissions) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BluetoothPermissionScreen(onRequest: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("Bluetooth Permission Required", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Bluetooth access is needed to scan for and connect to the payment terminal.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRequest) { Text("Grant Permissions") }
        }
    }
}
```

---

### Step 3 — Replace TerminalSettingsScreen.kt

Replace the entire content of `TerminalSettingsScreen.kt`:

```kotlin
package tech.path2ai.epos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tech.path2ai.epos.terminal.TerminalConnectionState
import tech.path2ai.epos.terminal.TerminalManager
import tech.path2ai.epos.ui.theme.OCGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalSettingsScreen(
    terminalManager: TerminalManager,
    onBack: () -> Unit
) {
    val connectionState by terminalManager.connectionState.collectAsState()
    val discoveredDevices by terminalManager.discoveredDevices.collectAsState()
    val isScanning by terminalManager.isScanning.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment Terminal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                when (connectionState) {
                                    is TerminalConnectionState.Connected -> OCGreen.copy(alpha = 0.15f)
                                    is TerminalConnectionState.Connecting -> Color(0xFFFF9800).copy(alpha = 0.15f)
                                    else -> Color(0xFFF44336).copy(alpha = 0.15f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (connectionState) {
                                is TerminalConnectionState.Connected -> Icons.Default.CheckCircle
                                is TerminalConnectionState.Connecting -> Icons.Default.Sync
                                else -> Icons.Default.Error
                            },
                            contentDescription = null,
                            tint = when (connectionState) {
                                is TerminalConnectionState.Connected -> OCGreen
                                is TerminalConnectionState.Connecting -> Color(0xFFFF9800)
                                else -> Color(0xFFF44336)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            when (connectionState) {
                                is TerminalConnectionState.Connected -> "Terminal Connected"
                                is TerminalConnectionState.Connecting -> "Connecting..."
                                else -> "No Terminal Connected"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            when (connectionState) {
                                is TerminalConnectionState.Connected -> "Ready to process payments"
                                is TerminalConnectionState.Connecting -> "Connecting to Path terminal..."
                                is TerminalConnectionState.Unavailable -> (connectionState as TerminalConnectionState.Unavailable).reason
                                else -> "Scan to discover nearby terminals"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    if (connectionState is TerminalConnectionState.Connecting) {
                        Spacer(Modifier.weight(1f))
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Path Terminal (BLE)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            if (connectionState is TerminalConnectionState.Connected) {
                OutlinedButton(
                    onClick = { terminalManager.disconnect() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Disconnect")
                }
            } else {
                Button(
                    onClick = { terminalManager.scanForDevices() },
                    enabled = !isScanning && connectionState !is TerminalConnectionState.Connecting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = OCGreen)
                ) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isScanning) "Scanning..." else "Scan for Terminal")
                    if (isScanning) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    }
                }

                if (discoveredDevices.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Discovered Devices", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                    discoveredDevices.forEach { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(device.name, modifier = Modifier.weight(1f))
                            Button(onClick = { terminalManager.connectToDevice(device) }) {
                                Text("Connect")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("About", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text("Terminal") },
                trailingContent = { Text("Path Terminal (BLE)", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
            )
            ListItem(
                headlineContent = { Text("Adapter") },
                trailingContent = { Text(terminalManager.adapterName, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
            )
        }
    }
}
```

---

### Step 4 — Add receipt model support

**4a. Update `TerminalModels.kt`** — Add `@Serializable` to `TerminalCardReceipt` and add `cardReceiptData` to `TerminalRefundResponse`.

Add `import kotlinx.serialization.Serializable` at the top (it is already imported in the file header area but verify it's present).

Change `TerminalCardReceipt`:
```kotlin
@Serializable
data class TerminalCardReceipt(
    val status: String,
    ...
```

Change `TerminalRefundResponse`:
```kotlin
data class TerminalRefundResponse(
    val succeeded: Boolean,
    val refundReference: String? = null,
    val cardReceiptData: TerminalCardReceipt? = null,
    val failureReason: String? = null
)
```

**4b. Update `Order.kt`** — Add `cardReceiptData` field to `CompletedOrder`.

Add import:
```kotlin
import tech.path2ai.epos.terminal.TerminalCardReceipt
```

Add field to `CompletedOrder` (after `authCode`):
```kotlin
val cardReceiptData: TerminalCardReceipt? = null,
```

**4c. Update `OrderManager.kt`** — Add `cardReceiptData` parameter to `recordSale()` and `recordRefund()`.

Add import:
```kotlin
import tech.path2ai.epos.terminal.TerminalCardReceipt
```

Add `cardReceiptData: TerminalCardReceipt? = null` parameter to `recordSale()` and pass it to `CompletedOrder(...)`.

Add `cardReceiptData: TerminalCardReceipt? = null` parameter to `recordRefund()` and pass it to `CompletedOrder(...)`.

**4d. Create `CardReceiptScreen.kt`** — New file at `app/src/main/kotlin/tech/path2ai/epos/ui/screens/CardReceiptScreen.kt`:

```kotlin
package tech.path2ai.epos.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.path2ai.epos.terminal.TerminalCardReceipt
import tech.path2ai.epos.ui.theme.OCGreen

@Composable
fun CardReceiptScreen(
    receipt: TerminalCardReceipt,
    amountPence: Int,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = OCGreen, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(8.dp))
        Text("£%.2f".format(amountPence / 100.0), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(receipt.status, color = OCGreen, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        ReceiptRow("Card", "${receipt.cardScheme} ${receipt.maskedPan}")
        ReceiptRow("Entry", receipt.entryMode)
        ReceiptRow("Verification", receipt.verificationMethod)
        ReceiptRow("AID", receipt.aid)
        ReceiptRow("Auth Code", receipt.authorisationCode)
        ReceiptRow("Ref", receipt.txnRef)
        ReceiptRow("Terminal", receipt.terminalId)
        ReceiptRow("Merchant", receipt.merchantId)
        ReceiptRow("Time", receipt.timestamp)

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = OCGreen)
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
```

---

### Step 5 — Update CardPaymentScreen.kt and RefundPaymentScreen.kt

**5a. `CardPaymentScreen.kt`** — Two changes:

1. Pass `cardReceiptData` to `recordSale()`:
```kotlin
orderManager.recordSale(
    ...
    authCode = response.authorisationCode,
    cardReceiptData = response.cardReceiptData   // ADD THIS LINE
)
```

2. In the `APPROVED` state, show `CardReceiptScreen` when receipt data is available:
```kotlin
CardPaymentState.APPROVED -> {
    Icon(Icons.Default.CheckCircle, ...)
    ...
    val receipt = saleResponse?.cardReceiptData
    if (receipt != null) {
        CardReceiptScreen(receipt = receipt, amountPence = total, onDone = onComplete)
    } else {
        Button(onClick = onComplete, ...) { Text("Done") }
    }
}
```

**5b. `RefundPaymentScreen.kt`** — Three changes:

1. Add state variable: `var refundReceipt by remember { mutableStateOf<TerminalCardReceipt?>(null) }`

2. In the success branch, capture receipt and pass to `recordRefund()`:
```kotlin
if (response.succeeded) {
    refundRef = response.refundReference
    refundReceipt = response.cardReceiptData          // ADD
    orderManager.markRefunded(order.id)
    orderManager.recordRefund(order, response.refundReference, response.cardReceiptData)  // ADD param
    state = RefundState.APPROVED
}
```

3. In the `APPROVED` state, show `CardReceiptScreen` when available:
```kotlin
RefundState.APPROVED -> {
    val receipt = refundReceipt
    if (receipt != null) {
        CardReceiptScreen(receipt = receipt, amountPence = order.amountPence, onDone = onDismiss)
    } else {
        Icon(Icons.Default.CheckCircle, ...)
        ...
        Button(onClick = onDismiss, ...) { Text("Done") }
    }
}
```

**5c. Customer tipping** — only needed if the harness's tipping feature is in use.

This harness ships with a customer-tipping feature wired through the OCPay
loopback: Settings → Payment Options has an **Allow tipping** switch, and
`TerminalSaleRequest` already carries a `promptForTip` flag.
`TerminalSaleResponse` already has the matching breakdown fields
(`baseAmountPence`, `tipAmountPence`, `totalAmountPence`, `tipPercentX10`,
`customerTimedOut`). Your `PathTerminalAdapter` needs to populate them
so the feature keeps working once the real SDK replaces OCPay.

- Call `get_code_example` with `operation='sale-with-tip'` and `platform='android'`
  for the canonical pattern.
- Inside `submitSale`, pass the request's `promptForTip` into
  `TransactionRequest.sale(..., promptForTip = ...)`.
- On the SDK result, map `baseAmountMinor` / `tipAmountMinor` /
  `totalAmountMinor` / `tipPercentX10` onto the matching
  `TerminalSaleResponse.*Pence` fields.
- Handle the SDK's new `CUSTOMER_TIMEOUT` state by returning a response
  with `authorised = false` and `customerTimedOut = true`. **Then update
  `CardPaymentScreen` to check `saleResponse?.customerTimedOut` and show
  a "Try again / Cancel" dialog instead of the "Payment Declined" one.**
- Call `explain_error` with `customer_timeout` for the full rationale.

---

### Step 6 — Update OrderHistoryScreen.kt

Add a **Receipt** button and upgrade the **Refund** button in the order list.

1. Add state variable: `var receiptOrder by remember { mutableStateOf<CompletedOrder?>(null) }`

2. Below the existing `refundOrder?.let { ... }` block, add:
```kotlin
receiptOrder?.let { order ->
    order.cardReceiptData?.let { receipt ->
        CardReceiptScreen(
            receipt = receipt,
            amountPence = order.amountPence,
            onDone = { receiptOrder = null }
        )
    }
}
```

3. Replace the existing `if (order.canRefund)` block in the order row with:
```kotlin
if (order.cardReceiptData != null) {
    Spacer(Modifier.width(4.dp))
    OutlinedButton(
        onClick = { receiptOrder = order },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text("Receipt", fontSize = 12.sp)
    }
}
if (order.canRefund) {
    Spacer(Modifier.width(4.dp))
    OutlinedButton(
        onClick = { refundOrder = order },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
    ) {
        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text("Refund", fontSize = 12.sp)
    }
}
```

Note: add `import androidx.compose.material.icons.automirrored.filled.Undo` for the Undo icon.

---

### Step 7 — Verify end-to-end

Run the app on a device with Bluetooth. With the Path POS Emulator running on a second device:

1. **Settings → Payment Terminal → Scan for Terminal** — the emulator appears in the list
2. Tap **Connect** — status dot turns green (first connection can take up to 15s on Android BLE)
3. Add items to cart → **Pay Now → Card** → present NFC tag on emulator → Approved → receipt screen shows with card details
4. **Order History** — sale shows Receipt + Refund buttons
5. Tap **Receipt** — full card receipt shown
6. Tap **Refund** → present NFC tag → refund approved → receipt screen shown
7. Order history now shows original order as REFUNDED + a new REFUND entry, both with Receipt buttons

---

## Key Interfaces

### PaymentTerminalAdapter (DO NOT MODIFY)

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

### SDK ConnectionState sealed class

```kotlin
sealed class ConnectionState {
    data object Idle        : ConnectionState()
    data object Scanning    : ConnectionState()
    data object Connecting  : ConnectionState()
    data object Connected   : ConnectionState()
    data object Disconnected: ConnectionState()
    data class  Error(val message: String) : ConnectionState()
}
```

### TerminalConnectionState (in TerminalModels.kt — DO NOT MODIFY)

```kotlin
sealed class TerminalConnectionState {
    data object Disconnected  : TerminalConnectionState()
    data object Connecting    : TerminalConnectionState()
    data object Connected     : TerminalConnectionState()
    data class  Unavailable(val reason: String) : TerminalConnectionState()
}
```

## Build

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```

Minimum SDK: API 26 (Android 8.0). Target: API 35.

## Important Notes

- **DO NOT** switch to a composite build or local project references. Use JitPack v1.3 as specified.
- **DO NOT** modify `PaymentTerminalAdapter.kt` or `TerminalManager.kt`.
- Connection takes up to 15s on first attempt — this is normal Android BLE behaviour.
- The `connectMutex` in `PathTerminalAdapter` prevents a double-connect race condition — keep it.
