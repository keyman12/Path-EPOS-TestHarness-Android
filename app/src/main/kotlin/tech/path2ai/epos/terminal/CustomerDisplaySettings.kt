package tech.path2ai.epos.terminal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Idle customer-display branding (a merchant logo) — the loopback twin of the
 * demo's feature. On a real terminal the SDK paints this on the customer screen
 * on connect and re-shows it after each transaction (attract mode); here the
 * OCPay simulator has no physical screen, so [OCPayTerminalAdapter.setIdleBranding]
 * is a logged no-op. This keeps the upload / resize / toggle settings flow at
 * full parity with the demo.
 */
class CustomerDisplayBranding(
    val imageBytes: ByteArray?,
    val caption: String? = null,
)

object CustomerDisplaySettings {

    private const val PREFS_NAME = "epos_customer_display"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_CAPTION = "caption"
    private const val LOGO_FILE = "customer_display_logo.png"
    private const val DEFAULT_ASSET = "pathcafe-logo.png"

    /** Longest edge (px) the uploaded logo is scaled down to — a customer screen
     *  is small and low-resolution, keeping the stored/encoded image small. */
    const val MAX_DIMENSION = 480

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    fun caption(context: Context): String = prefs(context).getString(KEY_CAPTION, "") ?: ""

    fun setCaption(context: Context, caption: String) {
        prefs(context).edit().putString(KEY_CAPTION, caption.trim()).apply()
    }

    private fun logoFile(context: Context) = File(context.filesDir, LOGO_FILE)

    fun hasCustomLogo(context: Context): Boolean = logoFile(context).exists()

    /** The current logo bytes — the uploaded one, or the bundled sample default. */
    fun logoBytes(context: Context): ByteArray =
        if (hasCustomLogo(context)) logoFile(context).readBytes()
        else context.assets.open(DEFAULT_ASSET).use { it.readBytes() }

    fun resetLogo(context: Context) {
        logoFile(context).delete()
    }

    /**
     * Scale [source] to fit within [MAX_DIMENSION] (aspect preserved), re-encode
     * as PNG, and store it as the custom logo. Returns the stored bytes, or null
     * if [source] couldn't be decoded as an image.
     */
    fun saveLogo(context: Context, source: ByteArray): ByteArray? {
        val bmp = BitmapFactory.decodeByteArray(source, 0, source.size) ?: return null
        val scaled = scaleToFit(bmp, MAX_DIMENSION)
        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        if (scaled !== bmp) scaled.recycle()
        bmp.recycle()
        logoFile(context).writeBytes(bytes)
        return bytes
    }

    private fun scaleToFit(bmp: Bitmap, max: Int): Bitmap {
        val w = bmp.width
        val h = bmp.height
        if (w <= max && h <= max) return bmp
        val ratio = minOf(max.toFloat() / w, max.toFloat() / h)
        return Bitmap.createScaledBitmap(
            bmp,
            (w * ratio).toInt().coerceAtLeast(1),
            (h * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
