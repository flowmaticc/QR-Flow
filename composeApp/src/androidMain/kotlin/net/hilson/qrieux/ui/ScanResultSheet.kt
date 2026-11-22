package net.hilson.qrieux.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.hilson.qrieux.R
import net.hilson.qrieux.util.QrContentType
import net.hilson.qrieux.AndroidContext
import net.hilson.qrieux.addCalendarEvent
import net.hilson.qrieux.addContact
import net.hilson.qrieux.connectToWifi
import net.hilson.qrieux.copyToClipboard
import net.hilson.qrieux.dialPhone
import net.hilson.qrieux.openMap
import net.hilson.qrieux.openOtherUri
import net.hilson.qrieux.openUrl
import net.hilson.qrieux.sendEmail
import net.hilson.qrieux.sendSms
import net.hilson.qrieux.shareText
import net.hilson.qrieux.showToast
import net.hilson.qrieux.security.QrRiskAnalyzer
import net.hilson.qrieux.security.RiskReason
import net.hilson.qrieux.security.RiskReport
import org.jetbrains.compose.resources.stringResource as sharedStringResource
import qr_scanner.composeapp.generated.resources.Res
import qr_scanner.composeapp.generated.resources.action_add_event
import qr_scanner.composeapp.generated.resources.action_open_app
import qr_scanner.composeapp.generated.resources.action_open_map
import qr_scanner.composeapp.generated.resources.action_send_sms
import qr_scanner.composeapp.generated.resources.history_forget_scan
import qr_scanner.composeapp.generated.resources.incognito_on
import qr_scanner.composeapp.generated.resources.open_failed
import qr_scanner.composeapp.generated.resources.risk_blocked

@Composable
fun ScanResultOverlay(
    contentType: QrContentType,
    onDismiss: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    incognito: Boolean = false,
    /** Non-null when the scan was saved: lets the user drop it from history right away. */
    onForget: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val report = remember(contentType) { QrRiskAnalyzer.analyze(contentType) }
    val guard = rememberRiskGuard(report)
    val rawValue = QrRiskAnalyzer.revealHiddenCharacters(contentType.displayText())

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
    ) {
        val frame = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
            .windowInsetsPadding(WindowInsets.safeDrawing)

        if (isLandscape) {
            // Stacked, the buttons alone are taller than a landscape phone, and no
            // arrangement of full-size targets fits. Side by side the actions get the
            // whole height instead of what the content leaves them.
            Row(
                modifier = frame.padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    ResultTitle(incognito)
                    Spacer(modifier = Modifier.height(16.dp))
                    // The column scrolls, so the card needs a bounded height of its own.
                    ResultCard(rawValue, Modifier.heightIn(max = (configuration.screenHeightDp * 0.4f).dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    RiskPanel(report)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        ResultActions(contentType, report, guard, context)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    ScanAgainButton(onDismiss)
                    ForgetButton(onForget)
                }
            }
        } else {
            // Portrait: the column scrolls so the actions stay reachable, and the card is
            // capped so a long payload cannot push them far down.
            val maxContentHeight = (configuration.screenHeightDp * 0.3f).dp
            Column(
                modifier = frame
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ResultTitle(incognito)
                Spacer(modifier = Modifier.height(16.dp))
                ResultCard(rawValue, Modifier.heightIn(max = maxContentHeight))
                Spacer(modifier = Modifier.height(16.dp))
                RiskPanel(report)
                Spacer(modifier = Modifier.height(24.dp))
                ResultActions(contentType, report, guard, context)
                Spacer(modifier = Modifier.height(16.dp))
                ScanAgainButton(onDismiss)
                ForgetButton(onForget)
            }
        }
    }
    RiskGuardDialog(guard)
}

@Composable
private fun ResultTitle(incognito: Boolean) {
    Text(
        text = stringResource(R.string.scan_result_title),
        color = Color.White,
        fontSize = 28.sp,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
    if (incognito) {
        Text(
            text = sharedStringResource(Res.string.incognito_on),
            color = Color(0xFFB39DDB),
            fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth().semantics { testTag = "result_incognito_label" },
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ResultCard(rawValue: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = rawValue,
            modifier = Modifier
                .semantics { testTag = "scan_result_content" }
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            fontSize = 20.sp,
            color = Color.Black
        )
    }
}

@Composable
private fun ResultActions(
    contentType: QrContentType,
    report: RiskReport,
    guard: RiskGuard,
    context: android.content.Context
) {
    val p = AndroidContext(context)
    val toastCopied = stringResource(R.string.toast_copied)
    val clipboardLabel = stringResource(R.string.clipboard_label_qr)
    val shareTitle = stringResource(R.string.action_share)
    val openFailed = sharedStringResource(Res.string.open_failed)
    val copy: (String, Boolean) -> Unit = { text, sensitive ->
        copyToClipboard(p, text, clipboardLabel, sensitive)
        showToast(p, toastCopied)
    }
    val share: (String) -> Unit = { shareText(p, it, shareTitle) }
    val mmi = RiskReason.MMI_CODE in report.reasons

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (contentType) {
            is QrContentType.Url -> {
                val scheme = contentType.url.substringBefore(':').lowercase()
                if (scheme == "http" || scheme == "https") {
                    ActionButton(stringResource(R.string.action_open_browser), Icons.Default.OpenInBrowser, danger = report) {
                        guard.run { openUrl(p, contentType.url) }
                    }
                }
                ActionButton(stringResource(R.string.action_copy), Icons.Default.ContentCopy) { copy(contentType.url, false) }
                ActionButton(stringResource(R.string.action_share), Icons.Default.Share) { share(contentType.url) }
            }
            is QrContentType.Email -> {
                ActionButton(stringResource(R.string.action_send_email), Icons.AutoMirrored.Filled.Send, danger = report) {
                    guard.run { sendEmail(p, contentType.email) }
                }
                ActionButton(stringResource(R.string.action_copy), Icons.Default.ContentCopy) { copy(contentType.email, false) }
                ActionButton(stringResource(R.string.action_share), Icons.Default.Share) { share(contentType.email) }
            }
            is QrContentType.Phone -> {
                if (!mmi) {
                    ActionButton(stringResource(R.string.action_call), Icons.Default.Phone, danger = report) {
                        guard.run { dialPhone(p, contentType.phone) }
                    }
                } else BlockedLabel()
                ActionButton(stringResource(R.string.action_copy), Icons.Default.ContentCopy) { copy(contentType.phone, false) }
                ActionButton(stringResource(R.string.action_share), Icons.Default.Share) { share(contentType.phone) }
            }
            is QrContentType.Sms -> {
                if (!mmi) {
                    ActionButton(sharedStringResource(Res.string.action_send_sms), Icons.Default.Sms, danger = report) {
                        guard.run { sendSms(p, contentType.number, contentType.body) }
                    }
                } else BlockedLabel()
                ActionButton(stringResource(R.string.action_copy), Icons.Default.ContentCopy) { copy(contentType.displayText(), false) }
                ActionButton(stringResource(R.string.action_share), Icons.Default.Share) { share(contentType.displayText()) }
            }
            is QrContentType.Wifi -> {
                val toastWifiSent = stringResource(R.string.toast_wifi_sent)
                ActionButton(stringResource(R.string.action_connect_wifi), Icons.Default.Wifi, danger = report) {
                    guard.run {
                        connectToWifi(p, contentType.ssid, contentType.password, contentType.authType, contentType.hidden)
                        showToast(p, toastWifiSent)
                    }
                }
                if (contentType.password.isNotEmpty()) {
                    // Passwords are always copied as sensitive: hidden from previews and cleared after a minute.
                    ActionButton(stringResource(R.string.wifi_copy_password), Icons.Default.ContentCopy) { copy(contentType.password, true) }
                }
                ActionButton(stringResource(R.string.action_share), Icons.Default.Share) { share(contentType.ssid) }
            }
            is QrContentType.Contact -> {
                ActionButton(stringResource(R.string.action_add_contact), Icons.Default.PersonAdd, danger = report) {
                    guard.run { addContact(p, contentType.rawVCard) }
                }
                if (contentType.phone.isNotEmpty() && !mmi) {
                    ActionButton(stringResource(R.string.action_call), Icons.Default.Phone) {
                        guard.run { dialPhone(p, contentType.phone) }
                    }
                }
                if (contentType.email.isNotEmpty()) {
                    ActionButton(stringResource(R.string.action_send_email), Icons.AutoMirrored.Filled.Send) {
                        guard.run { sendEmail(p, contentType.email) }
                    }
                }
                ActionButton(stringResource(R.string.action_share), Icons.Default.Share) { share(contentType.rawVCard) }
            }
            is QrContentType.Geo -> {
                ActionButton(sharedStringResource(Res.string.action_open_map), Icons.Default.Map) {
                    openMap(p, contentType.latitude, contentType.longitude, contentType.query)
                }
                ActionButton(stringResource(R.string.action_copy), Icons.Default.ContentCopy) { copy("${contentType.latitude},${contentType.longitude}", false) }
                ActionButton(stringResource(R.string.action_share), Icons.Default.Share) { share(contentType.raw) }
            }
            is QrContentType.Event -> {
                ActionButton(sharedStringResource(Res.string.action_add_event), Icons.Default.Event, danger = report) {
                    guard.run {
                        addCalendarEvent(p, contentType.summary, contentType.start, contentType.end, contentType.location, contentType.description)
                    }
                }
                ActionButton(stringResource(R.string.action_copy), Icons.Default.ContentCopy) { copy(contentType.displayText(), false) }
                ActionButton(stringResource(R.string.action_share), Icons.Default.Share) { share(contentType.displayText()) }
            }
            is QrContentType.OtherUri -> {
                if (QrRiskAnalyzer.isBlockedScheme(contentType.scheme)) {
                    BlockedLabel()
                } else {
                    ActionButton(sharedStringResource(Res.string.action_open_app), Icons.AutoMirrored.Filled.OpenInNew, danger = report) {
                        guard.run { if (!openOtherUri(p, contentType.uri)) showToast(p, openFailed) }
                    }
                }
                // A 2FA secret is as sensitive as a password.
                val sensitive = contentType.scheme.startsWith("otpauth")
                ActionButton(stringResource(R.string.action_copy), Icons.Default.ContentCopy) { copy(contentType.uri, sensitive) }
                if (!sensitive) ActionButton(stringResource(R.string.action_share), Icons.Default.Share) { share(contentType.uri) }
            }
            is QrContentType.Text -> {
                ActionButton(stringResource(R.string.action_copy), Icons.Default.ContentCopy) { copy(contentType.text, false) }
                ActionButton(stringResource(R.string.action_share), Icons.Default.Share) { share(contentType.text) }
            }
        }
    }
}

@Composable
private fun BlockedLabel() {
    Row(
        modifier = Modifier.fillMaxWidth().semantics { testTag = "action_blocked" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFFF8A80), modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        Text(sharedStringResource(Res.string.risk_blocked), color = Color(0xFFFF8A80), fontSize = 20.sp)
    }
}

@Composable
private fun ScanAgainButton(onDismiss: () -> Unit) {
    SecondaryButton(stringResource(R.string.action_scan_again), Icons.Default.QrCodeScanner, onDismiss)
}

@Composable
private fun ForgetButton(onForget: (() -> Unit)?) {
    if (onForget == null) return
    Spacer(Modifier.height(8.dp))
    TextButton(
        onClick = onForget,
        modifier = Modifier.fillMaxWidth().semantics { testTag = "forget_scan" }
    ) {
        Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.width(8.dp))
        Text(sharedStringResource(Res.string.history_forget_scan), color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp)
    }
}

@Composable
private fun ActionButton(text: String, icon: ImageVector, danger: RiskReport? = null, onClick: () -> Unit) {
    val warn = danger?.needsConfirmation == true
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (warn) Color(0xFF5D4037) else MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        )
    ) {
        Icon(
            imageVector = if (warn) Icons.Default.Warning else icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(text = text, fontSize = 22.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SecondaryButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color.White
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(text = text, fontSize = 22.sp, textAlign = TextAlign.Center)
    }
}
