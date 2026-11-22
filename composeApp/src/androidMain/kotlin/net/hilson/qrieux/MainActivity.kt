@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package net.hilson.qrieux

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    private val sharedUri = mutableStateOf<Uri?>(null)
    private val shareTimestamp = mutableLongStateOf(0L)
    private val sharedText = mutableStateOf<String?>(null)
    private val shareTextTimestamp = mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        cleanShareCache(this)
        handleIncognitoIntent(intent)
        handleShareIntent(intent)
        publishIncognitoShortcut()

        // The activity is exported: test-only extras are honoured on debuggable builds only,
        // so other apps cannot drive a release build into demo mode.
        val debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val screenshotBg = if (debuggable) intent?.getStringExtra("SCREENSHOT_BACKGROUND") else null
        val demoCamera = debuggable && (intent?.getBooleanExtra("DEMO_CAMERA", false) ?: false)

        setContent {
            // testTagsAsResourceId makes Compose testTag appear as resource-id in the
            // accessibility tree, so UiAutomator can find elements by resourceId
            // (locale-agnostic, unlike text/contentDescription).
            Box(modifier = Modifier.semantics { testTagsAsResourceId = true }) {
                App(
                    sharedImageUri = sharedUri.value,
                    shareTimestamp = shareTimestamp.longValue,
                    screenshotBackground = screenshotBg,
                    sharedText = sharedText.value,
                    shareTextTimestamp = shareTextTimestamp.longValue,
                    demoCamera = demoCamera
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncognitoIntent(intent)
        handleShareIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        cleanShareCache(this)
    }

    private fun handleIncognitoIntent(intent: Intent?) {
        // Only ever turns incognito on, so any app may send it without risk.
        if (intent?.action == ACTION_SCAN_INCOGNITO) {
            getSharedPreferences("qrieux", MODE_PRIVATE).edit()
                .putBoolean("qf_incognito", true)
                .putInt("qf_incognito_last_seen_min", (System.currentTimeMillis() / 60_000L).toInt())
                .apply()
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun publishIncognitoShortcut() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        try {
            val manager = getSystemService(android.content.pm.ShortcutManager::class.java) ?: return
            val label = getString(R.string.shortcut_scan_incognito)
            val shortcut = android.content.pm.ShortcutInfo.Builder(this, "scan_incognito")
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_shortcut_incognito))
                .setIntent(Intent(this, MainActivity::class.java).setAction(ACTION_SCAN_INCOGNITO))
                .build()
            manager.dynamicShortcuts = listOf(shortcut)
        } catch (_: Exception) {}
    }

    companion object {
        const val ACTION_SCAN_INCOGNITO = "net.hilson.qrieux.action.SCAN_INCOGNITO"
        private const val MAX_VCARD_BYTES = 256 * 1024
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return

        val mimeType = intent.type ?: ""
        when {
            mimeType.startsWith("image/") -> {
                sharedUri.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                shareTimestamp.longValue = System.currentTimeMillis()
            }
            mimeType == "text/x-vcard" || mimeType == "text/vcard" -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let {
                    try {
                        val text = readAndStripVCard(it)
                        if (text != null) {
                            sharedText.value = text
                            shareTextTimestamp.longValue = System.currentTimeMillis()
                        }
                    } catch (_: Exception) { }
                }
            }
            mimeType.startsWith("text/") -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (text != null) {
                    sharedText.value = text
                    shareTextTimestamp.longValue = System.currentTimeMillis()
                }
            }
        }
    }

    private fun readAndStripVCard(uri: Uri): String? {
        // A shared file can be arbitrarily large: read at most 256 KB instead of the whole stream.
        val raw = contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(MAX_VCARD_BYTES)
            var total = 0
            while (total < buffer.size) {
                val n = input.read(buffer, total, buffer.size - total)
                if (n < 0) break
                total += n
            }
            String(buffer, 0, total, Charsets.UTF_8)
        } ?: return null
        val allowedPrefixes = listOf(
            "BEGIN:", "END:", "VERSION:", "N:", "N;", "FN:", "FN;",
            "TEL:", "TEL;", "EMAIL:", "EMAIL;", "ORG:", "ORG;",
            "TITLE:", "TITLE;", "ADR:", "ADR;", "URL:", "URL;",
            "BDAY:", "NOTE:"
        )
        val lines = raw.lines()
        val result = mutableListOf<String>()
        var skipContinuation = false
        for (line in lines) {
            if (line.isBlank()) continue
            if (line.startsWith(" ") || line.startsWith("\t")) {
                if (!skipContinuation) result.add(line)
                continue
            }
            val upper = line.trim().uppercase()
            val allowed = allowedPrefixes.any { upper.startsWith(it) }
            skipContinuation = !allowed
            if (allowed) result.add(line.trim())
        }
        return result.joinToString("\r\n")
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
