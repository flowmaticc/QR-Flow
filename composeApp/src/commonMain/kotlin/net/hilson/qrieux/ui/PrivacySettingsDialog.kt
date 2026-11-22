package net.hilson.qrieux.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import net.hilson.qrieux.PlatformContext
import net.hilson.qrieux.history.PrivacySettings
import org.jetbrains.compose.resources.stringResource
import qr_scanner.composeapp.generated.resources.*

@Composable
fun PrivacySettingsDialog(platformContext: PlatformContext, onDismiss: () -> Unit) {
    var historyEnabled by remember { mutableStateOf(PrivacySettings.historyEnabled(platformContext)) }
    var saveWifi by remember { mutableStateOf(PrivacySettings.saveWifiPasswords(platformContext)) }
    var retention by remember { mutableStateOf(PrivacySettings.retentionDays(platformContext)) }
    var incognito by remember { mutableStateOf(PrivacySettings.incognito(platformContext)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.history_settings)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SwitchRow(stringResource(Res.string.incognito_mode), incognito, "setting_incognito") {
                    incognito = it; PrivacySettings.setIncognito(platformContext, it)
                }
                Text(
                    stringResource(Res.string.incognito_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SwitchRow(stringResource(Res.string.settings_history_enabled), historyEnabled, "setting_history_enabled") {
                    historyEnabled = it; PrivacySettings.setHistoryEnabled(platformContext, it)
                }
                SwitchRow(stringResource(Res.string.settings_save_wifi_passwords), saveWifi, "setting_save_wifi") {
                    saveWifi = it; PrivacySettings.setSaveWifiPasswords(platformContext, it)
                }
                Text(stringResource(Res.string.settings_retention), style = MaterialTheme.typography.titleSmall)
                PrivacySettings.RETENTION_OPTIONS.forEach { days ->
                    val label = when (days) {
                        1 -> Res.string.retention_1
                        7 -> Res.string.retention_7
                        30 -> Res.string.retention_30
                        else -> Res.string.retention_0
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { retention = days; PrivacySettings.setRetentionDays(platformContext, days) }
                            .semantics { testTag = "retention_$days" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = retention == days, onClick = {
                            retention = days; PrivacySettings.setRetentionDays(platformContext, days)
                        })
                        Text(stringResource(label))
                    }
                }
                Spacer(Modifier.padding(top = 4.dp))
                Text(
                    stringResource(Res.string.settings_privacy_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.semantics { testTag = "settings_done" }) {
                Text(stringResource(Res.string.settings_done))
            }
        }
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { testTag = tag })
    }
}
