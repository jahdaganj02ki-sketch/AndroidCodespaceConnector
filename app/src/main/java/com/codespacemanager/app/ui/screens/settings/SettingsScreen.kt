package com.codespacemanager.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codespacemanager.app.domain.model.Geo
import com.codespacemanager.app.platform.customtabs.CustomTabsHelper
import com.codespacemanager.app.ui.components.AppCard
import com.codespacemanager.app.ui.components.MonoLabel
import com.codespacemanager.app.ui.components.SecondaryButton
import com.codespacemanager.app.ui.components.SurfacePill
import com.codespacemanager.app.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenHelp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Message -> Unit
                is SettingsEvent.UpdateAvailable -> {
                    CustomTabsHelper.open(context, event.url)
                }
            }
        }
    }

    Scaffold(
        containerColor = colors.canvas,
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surfaceInset,
                    titleContentColor = colors.textPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Erweiterte Konfiguration",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Die ausgelieferte App bringt bereits eine OAuth-App mit. Hier " +
                            "können eigene Client-ID und Client-Secret hinterlegt werden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.settings.clientIdOverride,
                        onValueChange = viewModel::setClientId,
                        label = { Text("Client-ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.settings.clientSecretOverride,
                        onValueChange = viewModel::setClientSecret,
                        label = { Text("Client-Secret") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    MonoLabel("Callback: com.codespacemanager.app://oauth/callback")
                }
            }

            AppCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Startoptionen",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Region", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Geo.entries.forEach { geo ->
                            SurfacePill(
                                label = geo.label,
                                selected = geo == state.settings.defaultGeo,
                                onClick = { viewModel.setGeo(geo) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Maschine",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("basicLinux32gb", "standardLinux32gb", "premiumLinux64gb").forEach { machine ->
                            SurfacePill(
                                label = machine,
                                selected = machine == state.settings.defaultMachine,
                                onClick = { viewModel.setMachine(machine) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Idle-Timeout (Minuten)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60, 120).forEach { minutes ->
                            SurfacePill(
                                label = "$minutes",
                                selected = minutes == state.settings.idleTimeoutMinutes,
                                onClick = { viewModel.setIdleTimeout(minutes) },
                            )
                        }
                    }
                }
            }

            AppCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Port-Sichtbarkeit",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Beim Verlassen des Editors automatisch privat schalten",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = state.settings.autoPrivatePortOnExit,
                            onCheckedChange = viewModel::setAutoPrivate,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Inaktivitäts-Zeitfenster: ${state.settings.idlePrivatePortMinutes} Minuten",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 15, 30).forEach { minutes ->
                            SurfacePill(
                                label = "$minutes Min.",
                                selected = minutes == state.settings.idlePrivatePortMinutes,
                                onClick = { viewModel.setIdlePrivateMinutes(minutes) },
                            )
                        }
                    }
                }
            }

            AppCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Erweiterungen",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.extensionText,
                        onValueChange = viewModel::setExtensions,
                        label = { Text("IDs, eine pro Zeile") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(text = "Speichern", onClick = viewModel::saveExtensions)
                        SecondaryButton(text = "Open VSX abfragen", onClick = viewModel::resolveExtensions)
                    }
                    Spacer(Modifier.height(6.dp))
                    MonoLabel("Änderungen wirken bei neuen Codespaces bzw. nach einem Rebuild.")
                }
            }

            AppCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Über die App",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    MonoLabel("Version ${state.currentVersion}")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(
                            text = "Update prüfen",
                            onClick = viewModel::checkUpdate,
                            enabled = !state.checkingUpdate,
                        )
                        SecondaryButton(text = "Hilfe", onClick = onOpenHelp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
