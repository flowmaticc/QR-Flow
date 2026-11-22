package net.hilson.qrieux

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream
import java.io.File

class AndroidContext(val context: Context) : PlatformContext

@Suppress("DEPRECATION")
actual fun vibrate(context: PlatformContext) {
    val ctx = (context as AndroidContext).context
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        vibrator.vibrate(100)
    }
}

actual fun openUrl(context: PlatformContext, url: String) {
    val ctx = (context as AndroidContext).context
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {}
}

actual fun sendEmail(context: PlatformContext, email: String) {
    val ctx = (context as AndroidContext).context
    try {
        ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")))
    } catch (_: Exception) {}
}

actual fun dialPhone(context: PlatformContext, phone: String) {
    val ctx = (context as AndroidContext).context
    try {
        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
    } catch (_: Exception) {}
}

actual fun shareText(context: PlatformContext, text: String, title: String) {
    val ctx = (context as AndroidContext).context
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(intent, title))
    } catch (_: Exception) {}
}

actual fun shareImage(context: PlatformContext, pngData: ByteArray, title: String) {
    val ctx = (context as AndroidContext).context
    try {
        val shareDir = File(ctx.cacheDir, "shared_qr").apply { mkdirs() }
        val shareFile = File(shareDir, "qr-${System.currentTimeMillis()}.png")
        shareFile.outputStream().use { it.write(pngData) }

        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", shareFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(ctx.contentResolver, title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, title))
    } catch (_: Exception) {}
}

actual fun copyToClipboard(context: PlatformContext, text: String, label: String, sensitive: Boolean) {
    val ctx = (context as AndroidContext).context
    copyToClipboardInternal(ctx, text, label, sensitive || isIncognito(ctx))
}

private const val CLIPBOARD_CLEAR_DELAY_MS = 60_000L

internal fun copyToClipboardInternal(ctx: Context, text: String, label: String, sensitive: Boolean) {
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    if (sensitive) {
        // Android 13+ hides sensitive clips from the copy preview and keyboard suggestions.
        clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard.setPrimaryClip(clip)
    if (!sensitive) return

    // Clear it after a minute unless something else has been copied in the meantime.
    val main = android.os.Handler(android.os.Looper.getMainLooper())
    var replaced = false
    val listener = ClipboardManager.OnPrimaryClipChangedListener { replaced = true }
    main.post { clipboard.addPrimaryClipChangedListener(listener) }
    main.postDelayed({
        clipboard.removePrimaryClipChangedListener(listener)
        if (!replaced) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clipboard.clearPrimaryClip()
                else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            } catch (_: Exception) {}
        }
    }, CLIPBOARD_CLEAR_DELAY_MS)
}

actual fun showToast(context: PlatformContext, message: String) {
    val ctx = (context as AndroidContext).context
    // Long explanations need the long duration to be readable.
    Toast.makeText(ctx, message, if (message.length > 60) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

actual fun dismissPlatformInput(context: PlatformContext) {
    val ctx = (context as AndroidContext).context
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    (ctx as? android.app.Activity)?.currentFocus?.let { view ->
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}

actual fun connectToWifi(context: PlatformContext, ssid: String, password: String, authType: String, hidden: Boolean, onResult: (Boolean) -> Unit) {
    val ctx = (context as AndroidContext).context
    val suggestion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) buildWifiSuggestion(ssid, password, authType, hidden) else null
    if (suggestion != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bundle = Bundle().apply {
                putParcelableArrayList(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
            }
            try {
                ctx.startActivity(Intent(Settings.ACTION_WIFI_ADD_NETWORKS).apply { putExtras(bundle) })
            } catch (_: Exception) {}
            onResult(true)
        } else {
            // Android 10 only "suggests" the network and joins it whenever it decides, which looks like
            // nothing happened. Also copy the password (sensitive, self-clearing) and open the Wi-Fi panel
            // so the user can join right away.
            val wifiManager = ctx.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            wifiManager.removeNetworkSuggestions(listOf(suggestion))
            val status = wifiManager.addNetworkSuggestions(listOf(suggestion))
            if (password.isNotEmpty()) copyToClipboardInternal(ctx, password, "Wi-Fi", sensitive = true)
            try {
                ctx.startActivity(Intent(Settings.Panel.ACTION_WIFI))
            } catch (_: Exception) {
                try { ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } catch (_: Exception) {}
            }
            onResult(status == android.net.wifi.WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS)
        }
    } else {
        // Android 9 and older, or a network type suggestions cannot describe (WEP, enterprise):
        // copy the password as a sensitive clip that clears itself, then open Wi-Fi settings.
        if (password.isNotEmpty()) copyToClipboardInternal(ctx, password, "Wi-Fi", sensitive = true)
        try {
            ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        } catch (_: Exception) {}
        onResult(true)
    }
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
private fun buildWifiSuggestion(ssid: String, password: String, authType: String, hidden: Boolean): WifiNetworkSuggestion? {
    // Returns null for networks a suggestion cannot express (WEP, WPA-EAP): the caller falls back to settings.
    val builder = WifiNetworkSuggestion.Builder()
        .setSsid(ssid)
        .setIsHiddenSsid(hidden)
    val type = authType.uppercase()
    return try {
        when {
            type == "WPA3" || type == "SAE" -> builder.setWpa3Passphrase(password)
            type == "WPA" || type == "WPA2" || type == "WPA/WPA2" -> builder.setWpa2Passphrase(password)
            type == "WEP" || type.contains("EAP") -> return null
            // Many generators omit T: when there is a password it is almost always WPA2.
            password.isNotEmpty() -> builder.setWpa2Passphrase(password)
            else -> {} // open network
        }
        builder.build()
    } catch (_: IllegalArgumentException) {
        null // e.g. a passphrase outside the 8..63 range
    }
}

actual fun addContact(context: PlatformContext, vCardData: String) {
    val ctx = (context as AndroidContext).context
    val dir = File(ctx.cacheDir, "shared_qr").apply { mkdirs() }
    val file = File(dir, "contact.vcf")
    file.writeText(vCardData)
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "text/x-vcard")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try { ctx.startActivity(intent) } catch (_: Exception) {}
}

actual fun openAppSettings(context: PlatformContext) {
    val ctx = (context as AndroidContext).context
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", ctx.packageName, null)
    }
    ctx.startActivity(intent)
}

private const val PREFS_NAME = "qrieux"

actual fun isOnboardingCompleted(context: PlatformContext): Boolean {
    val ctx = (context as AndroidContext).context
    return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean("onboarding_completed", false)
}

actual fun setOnboardingCompleted(context: PlatformContext) {
    val ctx = (context as AndroidContext).context
    ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putBoolean("onboarding_completed", true).apply()
}

actual fun generateUuid(): String = java.util.UUID.randomUUID().toString()

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun loadHistoryJson(context: PlatformContext): String {
    val ctx = (context as AndroidContext).context
    val stored = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("history", "") ?: ""
    if (stored.isEmpty()) return ""
    if (!stored.startsWith(HistoryCipher.PREFIX)) {
        // Plain-text history from QRieux: encrypt it in place.
        saveHistoryJson(context, stored)
        return stored
    }
    return when (val read = HistoryCipher.decrypt(stored)) {
        is HistoryCipher.Read.Ok -> {
            HistoryCipher.writeBlocked = false
            read.text
        }
        HistoryCipher.Read.KeyLost -> {
            // The Keystore key is gone (some OS updates and restores wipe it): this history can never be
            // read again. Say so once, and let the next save start a new one with a new key.
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean("qf_history_unreadable", true)
                .remove("history")
                .apply()
            HistoryCipher.resetKey()
            ""
        }
        HistoryCipher.Read.Error -> {
            // Probably temporary: keep the stored data untouched and refuse to overwrite it.
            HistoryCipher.writeBlocked = true
            ""
        }
    }
}

actual fun saveHistoryJson(context: PlatformContext, json: String) {
    val ctx = (context as AndroidContext).context
    val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    if (HistoryCipher.writeBlocked && json != "[]") {
        prefs.edit().putBoolean("qf_history_write_failed", true).apply()
        return
    }
    if (json.isEmpty() || json == "[]") {
        prefs.edit().putString("history", json).apply()
        return
    }
    // A broken key is replaced once; the whole history is re-encrypted with the new one.
    val value = HistoryCipher.encrypt(json) ?: run { HistoryCipher.resetKey(); HistoryCipher.encrypt(json) }
    if (value == null) {
        // Never fall back to plain text: report it instead.
        prefs.edit().putBoolean("qf_history_write_failed", true).apply()
        return
    }
    prefs.edit().putString("history", value).apply()
}

internal fun isIncognito(ctx: Context): Boolean =
    ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("qf_incognito", false)

actual fun getBoolSetting(context: PlatformContext, key: String, default: Boolean): Boolean =
    (context as AndroidContext).context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("qf_$key", default)

actual fun setBoolSetting(context: PlatformContext, key: String, value: Boolean) {
    (context as AndroidContext).context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("qf_$key", value).apply()
}

actual fun getIntSetting(context: PlatformContext, key: String, default: Int): Int =
    (context as AndroidContext).context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("qf_$key", default)

actual fun setIntSetting(context: PlatformContext, key: String, value: Int) {
    (context as AndroidContext).context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("qf_$key", value).apply()
}

actual fun generateQrCode(content: String, size: Int): GeneratedQrCode? {
    return try {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2
        )
        val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)

        val pngBytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }

        GeneratedQrCode(
            image = bitmap.asImageBitmap(),
            pngData = pngBytes
        )
    } catch (_: Exception) {
        null
    }
}
