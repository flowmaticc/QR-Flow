package net.hilson.qrieux.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.hilson.qrieux.security.RiskLevel
import net.hilson.qrieux.security.RiskReason
import net.hilson.qrieux.security.RiskReport
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import qr_scanner.composeapp.generated.resources.*

private data class RiskStyle(val container: Color, val content: Color)

private fun styleFor(level: RiskLevel) = when (level) {
    RiskLevel.SAFE -> RiskStyle(Color(0xFF1B5E20), Color.White)
    RiskLevel.INFO -> RiskStyle(Color(0xFF37474F), Color.White)
    RiskLevel.CAUTION -> RiskStyle(Color(0xFFFFC107), Color(0xFF1A1A1A))
    RiskLevel.DANGER -> RiskStyle(Color(0xFFB3261E), Color.White)
}

private fun titleFor(level: RiskLevel): StringResource = when (level) {
    RiskLevel.SAFE -> Res.string.risk_level_safe
    RiskLevel.INFO -> Res.string.risk_level_info
    RiskLevel.CAUTION -> Res.string.risk_level_caution
    RiskLevel.DANGER -> Res.string.risk_level_danger
}

fun reasonText(reason: RiskReason): StringResource = when (reason) {
    RiskReason.DANGEROUS_SCHEME -> Res.string.risk_reason_dangerous_scheme
    RiskReason.USERINFO_TRICK -> Res.string.risk_reason_userinfo_trick
    RiskReason.IP_ADDRESS_HOST -> Res.string.risk_reason_ip_address_host
    RiskReason.PUNYCODE_HOST -> Res.string.risk_reason_punycode_host
    RiskReason.NON_ASCII_HOST -> Res.string.risk_reason_non_ascii_host
    RiskReason.BRAND_IMPERSONATION -> Res.string.risk_reason_brand_impersonation
    RiskReason.EXECUTABLE_DOWNLOAD -> Res.string.risk_reason_executable_download
    RiskReason.HIDDEN_CHARACTERS -> Res.string.risk_reason_hidden_characters
    RiskReason.ENCODED_HOST -> Res.string.risk_reason_encoded_host
    RiskReason.INSECURE_HTTP -> Res.string.risk_reason_insecure_http
    RiskReason.URL_SHORTENER -> Res.string.risk_reason_url_shortener
    RiskReason.SUSPICIOUS_TLD -> Res.string.risk_reason_suspicious_tld
    RiskReason.UNUSUAL_PORT -> Res.string.risk_reason_unusual_port
    RiskReason.PHISHING_KEYWORDS -> Res.string.risk_reason_phishing_keywords
    RiskReason.EMBEDDED_REDIRECT -> Res.string.risk_reason_embedded_redirect
    RiskReason.EXCESSIVE_SUBDOMAINS -> Res.string.risk_reason_excessive_subdomains
    RiskReason.APP_SCHEME -> Res.string.risk_reason_app_scheme
    RiskReason.CRYPTO_PAYMENT -> Res.string.risk_reason_crypto_payment
    RiskReason.OTP_SECRET -> Res.string.risk_reason_otp_secret
    RiskReason.VERY_LONG -> Res.string.risk_reason_very_long
    RiskReason.TEXT_CONTAINS_LINK -> Res.string.risk_reason_text_contains_link
    RiskReason.MMI_CODE -> Res.string.risk_reason_mmi_code
    RiskReason.PREMIUM_NUMBER -> Res.string.risk_reason_premium_number
    RiskReason.SMS_SHORT_NUMBER -> Res.string.risk_reason_sms_short_number
    RiskReason.SMS_PREFILLED_BODY -> Res.string.risk_reason_sms_prefilled_body
    RiskReason.OPEN_WIFI -> Res.string.risk_reason_open_wifi
    RiskReason.WEAK_WIFI -> Res.string.risk_reason_weak_wifi
}

/** Coloured verdict card shown above the actions of every scan result. */
@Composable
fun RiskPanel(report: RiskReport, modifier: Modifier = Modifier) {
    val style = styleFor(report.level)
    val icon = when (report.level) {
        RiskLevel.SAFE -> Icons.Default.CheckCircle
        RiskLevel.INFO -> Icons.Default.Info
        RiskLevel.CAUTION -> Icons.Default.Warning
        RiskLevel.DANGER -> Icons.Default.Dangerous
    }
    Card(
        modifier = modifier.fillMaxWidth().semantics { testTag = "risk_panel_${report.level.name.lowercase()}" },
        colors = CardDefaults.cardColors(containerColor = style.container, contentColor = style.content),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(titleFor(report.level)), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            report.realDomain?.let {
                Text(
                    stringResource(Res.string.risk_real_domain, it),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { testTag = "risk_real_domain" }
                )
            }
            report.host?.let { Text(stringResource(Res.string.risk_full_host, it), fontSize = 14.sp) }
            report.reasons.forEach { reason ->
                Text("• " + stringResource(reasonText(reason)), fontSize = 16.sp)
            }
            if (report.level >= RiskLevel.CAUTION) {
                Text(stringResource(Res.string.risk_offline_note), fontSize = 13.sp)
            }
        }
    }
}

/** Asks for confirmation before running an action on a code that raised warnings. */
@Stable
class RiskGuard(private val report: RiskReport) {
    var pending by mutableStateOf<(() -> Unit)?>(null)
        private set

    fun run(action: () -> Unit) {
        if (report.needsConfirmation) pending = action else action()
    }

    fun confirm() {
        val action = pending
        pending = null
        action?.invoke()
    }

    fun cancel() {
        pending = null
    }
}

@Composable
fun rememberRiskGuard(report: RiskReport): RiskGuard = remember(report) { RiskGuard(report) }

@Composable
fun RiskGuardDialog(guard: RiskGuard) {
    if (guard.pending == null) return
    AlertDialog(
        onDismissRequest = { guard.cancel() },
        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
        title = { Text(stringResource(Res.string.risk_confirm_title)) },
        text = { Text(stringResource(Res.string.risk_confirm_body)) },
        confirmButton = {
            TextButton(onClick = { guard.confirm() }, modifier = Modifier.semantics { testTag = "risk_confirm_continue" }) {
                Text(stringResource(Res.string.risk_confirm_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = { guard.cancel() }) { Text(stringResource(Res.string.risk_confirm_cancel)) }
        }
    )
}
