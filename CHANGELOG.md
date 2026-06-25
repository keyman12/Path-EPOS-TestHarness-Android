# Changelog — OrderChampion EPOS Test Harness (Android)

Human-readable history of notable changes, newest first.

**Versioning.** `MAJOR.MINOR` (e.g. `1.0` → `1.1`). A small/additive change bumps the
**minor** (`1.0` → `1.1`); a big or breaking change bumps the **major** (`1.0` → `2.0`).
The single source of truth is `versionName` in [`app/build.gradle.kts`](app/build.gradle.kts);
the About row reads it via `BuildConfig.VERSION_NAME`, so the two can't drift. When you bump,
also raise the integer `versionCode` by 1 and add a dated entry below. Policy:
`~/Developer/Path-PSDK-TestHarnesses/docs/VERSIONING.md`.

> This harness is **loopback-only**: every transaction runs through the simulated
> `OCPayTerminalAdapter` — no emulator, Verifone, or real-terminal link.

## 1.0 — 2026-06-25

Baseline: versioning + changelog mechanism established; About now reads the build version.
Current feature state (most recent capability last):
- Loopback payments through the simulated `OCPayTerminalAdapter`.
- Sale, linked refund, linked void; receipts (merchant/customer) + email receipts.
- Customer-display merchant logo / attract mode.
