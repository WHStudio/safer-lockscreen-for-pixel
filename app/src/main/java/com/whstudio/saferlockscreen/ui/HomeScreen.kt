package com.whstudio.saferlockscreen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whstudio.saferlockscreen.R
import com.whstudio.saferlockscreen.guard.GuardSettings

@Composable
internal fun HomeScreen(
    state: ProtectionState,
    onChangeSettings: ((GuardSettings) -> GuardSettings) -> Unit,
    onDisableAdmin: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    val statusTitle = stringResource(statusTitle(state))
    val statusBody = stringResource(statusBody(state))
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                StatusCard(title = statusTitle, body = statusBody, state = state)
                if (!state.deviceSecure) {
                    InfoCard(
                        title = stringResource(R.string.secure_lock_warning),
                        body = stringResource(R.string.hero_no_secure_lock),
                        warning = true,
                    )
                }
                Text(
                    text = stringResource(R.string.intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.section_setup),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.adminActive) {
                    LegacyAdminStep(onDisableAdmin)
                }
                AccessibilityStep(state, onOpenAccessibility)
                ProtectionStep(state, onChangeSettings)
                InfoCard(
                    title = stringResource(R.string.smart_lock_note),
                    body = null,
                    warning = false,
                )
                FooterLinks()
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, body: String, state: ProtectionState) {
    val container = when {
        state.fullyActive -> MaterialTheme.colorScheme.primaryContainer
        state.accessibilityEnabled -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onContainer = when {
        state.fullyActive -> MaterialTheme.colorScheme.onPrimaryContainer
        state.accessibilityEnabled -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val description = stringResource(R.string.cd_protection_status)
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (state.fullyActive) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                contentDescription = null,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Text(text = body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LegacyAdminStep(onDisableAdmin: () -> Unit) {
    StepCard(title = stringResource(R.string.legacy_admin_title), done = false) {
        Text(text = stringResource(R.string.legacy_admin_body), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onDisableAdmin) {
            Text(stringResource(R.string.step_admin_deactivate))
        }
    }
}

@Composable
private fun AccessibilityStep(state: ProtectionState, onOpenAccessibility: () -> Unit) {
    StepCard(title = stringResource(R.string.step_a11y_title), done = state.accessibilityEnabled) {
        Text(text = stringResource(R.string.step_a11y_body), style = MaterialTheme.typography.bodyMedium)
        if (state.accessibilityEnabled) {
            Text(
                text = stringResource(R.string.step_a11y_done),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        Button(onClick = onOpenAccessibility) {
            Text(stringResource(R.string.step_a11y_action))
        }
        Text(
            text = stringResource(R.string.step_a11y_restricted),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProtectionStep(
    state: ProtectionState,
    onChangeSettings: ((GuardSettings) -> GuardSettings) -> Unit,
) {
    val settings = state.settings
    StepCard(title = stringResource(R.string.step_protect_title), done = state.fullyActive) {
        SwitchRow(
            title = stringResource(R.string.protection_switch),
            summary = stringResource(R.string.protection_switch_summary),
            checked = settings.protectionEnabled,
            onCheckedChange = { enabled ->
                onChangeSettings { it.copy(protectionEnabled = enabled) }
            },
        )
        SwitchRow(
            title = stringResource(R.string.toggle_qs),
            summary = stringResource(R.string.toggle_qs_summary),
            checked = settings.blockQuickSettings,
            onCheckedChange = { enabled ->
                onChangeSettings { it.copy(blockQuickSettings = enabled) }
            },
        )
        SwitchRow(
            title = stringResource(R.string.toggle_power),
            summary = stringResource(R.string.toggle_power_summary),
            checked = settings.blockPowerMenu,
            onCheckedChange = { enabled ->
                onChangeSettings { it.copy(blockPowerMenu = enabled) }
            },
        )
    }
}

@Composable
private fun StepCard(title: String, done: Boolean, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (done) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun InfoCard(title: String, body: String?, warning: Boolean) {
    val container = if (warning) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val content = if (warning) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (warning) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(imageVector = Icons.Filled.Warning, contentDescription = null)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = title, style = MaterialTheme.typography.bodyMedium)
                        if (body != null) {
                            Text(text = body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                if (body != null) {
                    Text(text = body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private enum class FooterTopic(val title: Int, val body: Int) {
    LIMITATIONS(R.string.limitations_title, R.string.limitations_body),
    PRIVACY(R.string.privacy_title, R.string.privacy_body),
    UNINSTALL(R.string.uninstall_title, R.string.uninstall_body),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FooterLinks() {
    var topic by remember { mutableStateOf<FooterTopic?>(null) }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FooterTopic.entries.forEach { item ->
            Text(
                text = stringResource(item.title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { topic = item },
            )
        }
    }
    val open = topic
    if (open != null) {
        AlertDialog(
            onDismissRequest = { topic = null },
            title = { Text(stringResource(open.title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = stringResource(open.body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { topic = null }) {
                    Text(stringResource(R.string.footer_close))
                }
            },
        )
    }
}

private fun statusTitle(state: ProtectionState): Int {
    return when {
        !state.accessibilityEnabled -> R.string.status_setup
        !state.settings.protectionEnabled -> R.string.status_off
        else -> R.string.status_active
    }
}

private fun statusBody(state: ProtectionState): Int {
    return when {
        !state.accessibilityEnabled -> R.string.hero_need_accessibility
        !state.settings.protectionEnabled -> R.string.hero_off
        else -> R.string.hero_active
    }
}
