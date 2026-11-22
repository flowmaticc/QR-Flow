package net.hilson.qrieux

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.WindowManager
import java.io.File
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import net.hilson.qrieux.security.QrRiskAnalyzer

/** AES-256-GCM with a key that never leaves the Android Keystore. */
internal object HistoryCipher {
    const val PREFIX = "qfenc1:"
    private const val ALIAS = "qrflow_history_key"
    private const val IV_SIZE = 12

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String? = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        PREFIX + Base64.encodeToString(out, Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }

    fun decrypt(stored: String): String? = try {
        val data = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, IV_SIZE))
        String(cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}

actual fun setScreenSecure(context: PlatformContext, secure: Boolean) {
    val activity = (context as AndroidContext).context as? Activity ?: return
    if (secure) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
}

actual fun sendSms(context: PlatformContext, number: String, body: String) {
    val ctx = (context as AndroidContext).context
    try {
        ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).apply {
            if (body.isNotEmpty()) putExtra("sms_body", body)
        })
    } catch (_: Exception) {}
}

actual fun openMap(context: PlatformContext, latitude: Double, longitude: Double, query: String) {
    val ctx = (context as AndroidContext).context
    val label = if (query.isNotEmpty()) "(${Uri.encode(query)})" else ""
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude$label")))
    } catch (_: Exception) {}
}

private val ICAL_FORMATS = listOf(
    Triple(Regex("^\\d{8}T\\d{6}Z$"), "yyyyMMdd'T'HHmmss'Z'", true),
    Triple(Regex("^\\d{8}T\\d{6}$"), "yyyyMMdd'T'HHmmss", false),
    Triple(Regex("^\\d{8}T\\d{4}Z$"), "yyyyMMdd'T'HHmm'Z'", true),
    Triple(Regex("^\\d{8}T\\d{4}$"), "yyyyMMdd'T'HHmm", false),
    Triple(Regex("^\\d{8}$"), "yyyyMMdd", false),
)

/** Returns epoch millis and whether the value is a whole-day date. */
internal fun parseIcalDate(value: String): Pair<Long, Boolean>? {
    val v = value.trim()
    val (_, pattern, utc) = ICAL_FORMATS.firstOrNull { it.first.matches(v) } ?: return null
    return try {
        val fmt = SimpleDateFormat(pattern, Locale.US)
        if (utc) fmt.timeZone = TimeZone.getTimeZone("UTC")
        val ms = fmt.parse(v)?.time ?: return null
        ms to (pattern == "yyyyMMdd")
    } catch (_: Exception) {
        null
    }
}

actual fun addCalendarEvent(
    context: PlatformContext,
    summary: String,
    start: String,
    end: String,
    location: String,
    description: String
) {
    val ctx = (context as AndroidContext).context
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, summary)
        if (location.isNotEmpty()) putExtra(CalendarContract.Events.EVENT_LOCATION, location)
        if (description.isNotEmpty()) putExtra(CalendarContract.Events.DESCRIPTION, description)
        parseIcalDate(start)?.let { (ms, allDay) ->
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ms)
            if (allDay) putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        }
        parseIcalDate(end)?.let { (ms, _) -> putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ms) }
    }
    try { ctx.startActivity(intent) } catch (_: Exception) {}
}

actual fun openOtherUri(context: PlatformContext, uri: String): Boolean {
    val ctx = (context as AndroidContext).context
    val parsed = Uri.parse(uri)
    if (QrRiskAnalyzer.isBlockedScheme(parsed.scheme ?: "")) return false
    return try {
        // CATEGORY_BROWSABLE: only the apps a link on a web page could open, never internal activities.
        ctx.startActivity(Intent(Intent.ACTION_VIEW, parsed).addCategory(Intent.CATEGORY_BROWSABLE))
        true
    } catch (_: Exception) {
        false
    }
}

/** Shared QR images and contacts only need to live while the share sheet is open. */
fun cleanShareCache(ctx: Context, olderThanMs: Long = 10 * 60_000L) {
    val now = System.currentTimeMillis()
    File(ctx.cacheDir, "shared_qr").listFiles()?.forEach {
        if (now - it.lastModified() > olderThanMs) it.delete()
    }
    File(ctx.cacheDir, "contact.vcf").delete() // left behind by QRieux
}
