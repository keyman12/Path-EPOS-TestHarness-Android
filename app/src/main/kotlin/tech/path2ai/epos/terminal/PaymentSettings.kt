package tech.path2ai.epos.terminal

import android.content.Context

/**
 * Thin SharedPreferences wrapper for payment-related toggles.
 *
 * Currently just the "Allow tipping" flag. When on, card sales ask the
 * payment adapter to prompt the customer for a tip — the OCPay loopback
 * responds by picking a random preset (10% / 15% / 20%). A real payment
 * SDK integrated later would drive its own terminal UI.
 *
 * Kept as a plain object (no DI) so Settings and the payment screens can
 * both read/write without ceremony.
 */
object PaymentSettings {

    private const val PREFS_NAME = "epos_payment_settings"
    private const val KEY_ALLOW_TIPPING = "allow_tipping"
    private const val KEY_SIM_OUTCOME = "sim_outcome"
    private const val KEY_ALLOW_PREAUTH = "allow_preauth"
    private const val KEY_TAB_CAP_PENCE = "tab_cap_pence"

    fun isTippingAllowed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ALLOW_TIPPING, /* default */ true)
    }

    fun setTippingAllowed(context: Context, allowed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ALLOW_TIPPING, allowed).apply()
    }

    /**
     * The outcome the OCPay loopback should simulate on the next card sale.
     * Sticky — stays until changed — so the cashier can flip to DECLINE, run a
     * few sales, then flip back to APPROVE. APPROVE = normal approved sale.
     */
    fun simulatedOutcome(context: Context): SimulatedOutcome {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_SIM_OUTCOME, SimulatedOutcome.APPROVE.name)
        return runCatching { SimulatedOutcome.valueOf(name!!) }.getOrDefault(SimulatedOutcome.APPROVE)
    }

    fun setSimulatedOutcome(context: Context, outcome: SimulatedOutcome) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SIM_OUTCOME, outcome.name).apply()
    }

    /**
     * Whether pre-authorization (open a tab / hold, complete, void) is offered in
     * the UI. Mirrors a real terminal's `Merchant.PreAuthEnabled`. When off, the
     * Tabs entry point and the "Add to Tab" tender are hidden. Defaults ON so
     * fresh installs exercise the feature.
     */
    fun isPreAuthAllowed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ALLOW_PREAUTH, /* default */ true)
    }

    fun setPreAuthAllowed(context: Context, allowed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ALLOW_PREAUTH, allowed).apply()
    }

    /**
     * Upper limit (minor units) a bar/café tab may accrue to before more items
     * are blocked — independent of the pre-auth hold (the tab can exceed the
     * hold; it's reconciled at close). Defaults to £200.
     */
    fun tabCapPence(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_TAB_CAP_PENCE, /* default £200 */ 20_000)
    }

    fun setTabCapPence(context: Context, pence: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_TAB_CAP_PENCE, pence).apply()
    }

    // ── Terminal login / session credentials ────────────────────────────────
    // Shown in the Terminal settings as the connect-time login. The OCPay
    // loopback doesn't authenticate against anything, but the UI mirrors the
    // demo and a real payment SDK integrated later would consume these.
    private const val KEY_LOGIN_USER = "login_username"
    private const val KEY_LOGIN_PASS = "login_password"
    private const val KEY_LOGIN_SHIFT = "login_shift"
    private const val KEY_REFUND_PASS = "refund_password"

    fun loginUsername(context: Context): String =
        prefs(context).getString(KEY_LOGIN_USER, "user") ?: "user"

    fun setLoginUsername(context: Context, v: String) {
        prefs(context).edit().putString(KEY_LOGIN_USER, v.trim()).apply()
    }

    fun loginPassword(context: Context): String =
        prefs(context).getString(KEY_LOGIN_PASS, "password123") ?: "password123"

    fun setLoginPassword(context: Context, v: String) {
        prefs(context).edit().putString(KEY_LOGIN_PASS, v).apply()
    }

    fun loginShift(context: Context): String =
        prefs(context).getString(KEY_LOGIN_SHIFT, "shift123") ?: "shift123"

    fun setLoginShift(context: Context, v: String) {
        prefs(context).edit().putString(KEY_LOGIN_SHIFT, v.trim()).apply()
    }

    fun refundPassword(context: Context): String =
        prefs(context).getString(KEY_REFUND_PASS, "") ?: ""

    fun setRefundPassword(context: Context, v: String) {
        prefs(context).edit().putString(KEY_REFUND_PASS, v).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
