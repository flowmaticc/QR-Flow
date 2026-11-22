package net.hilson.qrieux

import androidx.compose.ui.graphics.ImageBitmap

interface PlatformContext

data class GeneratedQrCode(
    val image: ImageBitmap,
    val pngData: ByteArray
)

expect fun vibrate(context: PlatformContext)
expect fun openUrl(context: PlatformContext, url: String)
expect fun sendEmail(context: PlatformContext, email: String)
expect fun dialPhone(context: PlatformContext, phone: String)
expect fun shareText(context: PlatformContext, text: String, title: String)
expect fun shareImage(context: PlatformContext, pngData: ByteArray, title: String)
/**
 * [sensitive] hides the text from clipboard previews (Android 13+) and clears it after a minute.
 * Incognito mode makes every copy sensitive.
 */
expect fun copyToClipboard(context: PlatformContext, text: String, label: String, sensitive: Boolean = false)
expect fun showToast(context: PlatformContext, message: String)
expect fun dismissPlatformInput(context: PlatformContext)
expect fun openAppSettings(context: PlatformContext)
expect fun connectToWifi(context: PlatformContext, ssid: String, password: String, authType: String, hidden: Boolean, onResult: (Boolean) -> Unit = {})
expect fun isOnboardingCompleted(context: PlatformContext): Boolean
expect fun setOnboardingCompleted(context: PlatformContext)
expect fun addContact(context: PlatformContext, vCardData: String)
expect fun generateQrCode(content: String, size: Int): GeneratedQrCode?
expect fun generateUuid(): String
expect fun currentTimeMillis(): Long
expect fun loadHistoryJson(context: PlatformContext): String
expect fun saveHistoryJson(context: PlatformContext, json: String)

// --- QR-Flow additions ---
expect fun sendSms(context: PlatformContext, number: String, body: String)
expect fun openMap(context: PlatformContext, latitude: Double, longitude: Double, query: String)
expect fun addCalendarEvent(context: PlatformContext, summary: String, start: String, end: String, location: String, description: String)
/** Opens a non-web link (market:, otpauth:, bitcoin:…) in the app that handles it. Returns false if none does. */
expect fun openOtherUri(context: PlatformContext, uri: String): Boolean
expect fun getBoolSetting(context: PlatformContext, key: String, default: Boolean): Boolean
expect fun setBoolSetting(context: PlatformContext, key: String, value: Boolean)
expect fun getIntSetting(context: PlatformContext, key: String, default: Int): Int
expect fun setIntSetting(context: PlatformContext, key: String, value: Int)
/** Blocks screenshots, screen recording and the recent-apps thumbnail while [secure] is true. */
expect fun setScreenSecure(context: PlatformContext, secure: Boolean)
