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

    fun isTippingAllowed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ALLOW_TIPPING, /* default */ true)
    }

    fun setTippingAllowed(context: Context, allowed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ALLOW_TIPPING, allowed).apply()
    }
}
