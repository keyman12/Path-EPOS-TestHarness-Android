# Path EPOS Test Harness — Android

A complete Android EPOS app (coffee shop menu, cart, card and cash payments, order history) built with Jetpack Compose. Used to validate that an AI coding agent can integrate the Path Terminal SDK end-to-end without human intervention.

---

## What this is

The harness ships with a **simulated payment adapter** (`OCPayTerminalAdapter`) that auto-approves all transactions. The integration task is to replace it with a real `PathTerminalAdapter` that communicates with the Path POS Emulator over BLE.

The full integration — scan, connect, sale with receipt, refund with receipt, order history — is described in `CLAUDE.md`. An AI agent reading that file has everything it needs to complete the integration in one pass.

---

## One-line agent integration

From the project root:

```bash
node <(curl -s https://mcp.path2ai.tech/init.js) --agent claude
```

The CLI adds Gradle dependencies and runs a Claude Code agent. The agent completes all 6 integration steps automatically. Expected time: ~5 minutes.

---

## Manual integration

See `CLAUDE.md` for step-by-step instructions. The 6 steps are:

1. Create `PathTerminalAdapter.kt` (BLE adapter implementation)
2. Update `MainActivity.kt` (BLE permission handling)
3. Replace `TerminalSettingsScreen.kt` (scan/discover/connect UI)
4. Update model files + create `CardReceiptScreen.kt` (receipt data support)
5. Update `CardPaymentScreen.kt` + `RefundPaymentScreen.kt` (show receipt after approval)
6. Update `OrderHistoryScreen.kt` (Receipt + Refund buttons per order)

---

## Integration test flow

Once the SDK is wired in, test against the Path POS Emulator:

1. **Settings → Payment Terminal → Scan for Terminal** — emulator appears in list
2. **Connect** — status turns green (first connection can take up to 15s on Android BLE)
3. **Add items → Pay Now → Card** — present NFC tag on emulator → Approved → receipt screen
4. **Order History** — sale shows Receipt button (tap to view) and Refund button
5. **Refund** — present NFC tag on emulator → refund approved → receipt screen
6. **Order History** — original order marked REFUNDED, new REFUND entry with Receipt button

---

## Project structure

```
app/src/main/kotlin/tech/path2ai/epos/
├── EPOSApplication.kt
├── MainActivity.kt
├── terminal/
│   ├── PaymentTerminalAdapter.kt   — Interface (DO NOT MODIFY)
│   ├── TerminalModels.kt           — Data classes
│   ├── TerminalManager.kt          — ViewModel (DO NOT MODIFY)
│   └── OCPayTerminalAdapter.kt     — Simulated adapter (REPLACE)
├── managers/
│   ├── InventoryManager.kt
│   └── OrderManager.kt
├── models/
│   └── Cart.kt, Order.kt, Product.kt, Receipt.kt
└── ui/screens/
    ├── EPOSScreen.kt
    ├── PaymentScreen.kt
    ├── CardPaymentScreen.kt
    ├── CashPaymentScreen.kt
    ├── RefundPaymentScreen.kt
    ├── OrderHistoryScreen.kt
    ├── TerminalSettingsScreen.kt
    └── SplashScreen.kt
```

---

## Build

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```

Minimum SDK: API 26 (Android 8.0). Target: API 35.

---

## SDK

The Path Terminal SDK is published on JitPack:

```
com.github.keyman12.path-terminal-sdk-android:path-terminal-sdk:v1.3
```

See the [Android SDK README](https://github.com/keyman12/path-terminal-sdk-android) for full API reference.
