package com.codespacemanager.app.ui.screens.start

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codespacemanager.app.domain.model.Geo
import com.codespacemanager.app.domain.usecase.StartCodespaceState
import com.codespacemanager.app.platform.customtabs.CustomTabsHelper
import com.codespacemanager.app.ui.components.AppCard
import com.codespacemanager.app.ui.components.LoadingState
import com.codespacemanager.app.ui.components.PrimaryButton
import com.codespacemanager.app.ui.components.SecondaryButton
import com.codespacemanager.app.ui.components.SurfacePill
import com.codespacemanager.app.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartCodespaceScreen(
    repoFullName: String,
    onOpenEditor: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: StartCodespaceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val context = LocalContext.current

    LaunchedEffect(repoFullName) {
        viewModel.start(repoFullName)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StartEvent.OpenUrl -> CustomTabsHelper.open(context, event.url)
                is StartEvent.OpenEditor -> onOpenEditor(event.codespaceName)
                is StartEvent.Message -> Unit
            }
        }
    }

    Scaffold(
        containerColor = colors.canvas,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Codespace starten",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            CustomTabsHelper.open(context, "https://github.com/$repoFullName")
                        },
                    ) {
                        Icon(Icons.Outlined.OpenInBrowser, contentDescription = "Im Browser öffnen")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surfaceInset,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textPrimary,
                    actionIconContentColor = colors.textPrimary,
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
        ) {
            Text(
                text = state.repoName.ifBlank { repoFullName },
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(16.dp))

            if (state.loading) {
                LoadingState()
            }

            if (state.progress != null) {
                ProgressCard(state = state)
                Spacer(Modifier.height(16.dp))
            }

            if (state.error != null) {
                AppCard {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Fehler",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = state.error.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (state.progress == null && state.error == null) {
                OptionsCard(state = state, viewModel = viewModel)
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = "Starten",
                    onClick = { viewModel.start(repoFullName) },
                )
            }

            if (state.canCancel) {
                Spacer(Modifier.height(12.dp))
                SecondaryButton(text = "Abbrechen", onClick = viewModel::cancel)
            }
        }
    }

    state.plan?.let { plan ->
        AlertDialog(
            onDismissRequest = { viewModel.skipPreparation() },
            containerColor = colors.surfaceRaised,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text("devcontainer wird erweitert") },
            text = {
                Column {
                    Text(
                        text = "Der Editor (code-server) wird auf dem Branch ${plan.branch} " +
                            "ergänzt. Der Default-Branch bleibt unverändert.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = plan.diff.ifBlank { "Keine inhaltliche Änderung." },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        color = colors.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .horizontalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPreparation) {
                    Text("Übernehmen", color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::skipPreparation) {
                    Text("Original verwenden", color = colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun ProgressCard(state: StartUiState) {
    val colors = AppTheme.colors
    AppCard {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = progressTitle(state.progress),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = progressDetail(state.progress),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

private fun progressTitle(progress: StartCodespaceState?): String = when (progress) {
    null -> "Bereit"
    is StartCodespaceState.Idle -> "Bereit"
    is StartCodespaceState.CheckingRepo -> "Prüfe Repository"
    is StartCodespaceState.PreparingRepo -> "Bereite Branch vor"
    is StartCodespaceState.CreatingCodespace -> "Erzeuge Codespace"
    is StartCodespaceState.WaitingForCodespace -> "Warte auf Codespace"
    is StartCodespaceState.ConnectingPort -> "Öffne Editor-Port"
    is StartCodespaceState.PreparingExtensions -> "Erweiterungen werden vorbereitet"
    is StartCodespaceState.WaitingForEditor -> "Lade Editor"
    is StartCodespaceState.Ready -> "Editor ist bereit"
    is StartCodespaceState.Failed -> "Fehlgeschlagen"
    is StartCodespaceState.Cancelled -> "Abgebrochen"
}

private fun progressDetail(progress: StartCodespaceState?): String = when (progress) {
    null -> "Starte den Codespace, um fortzufahren."
    is StartCodespaceState.Idle -> "Starte den Codespace, um fortzufahren."
    is StartCodespaceState.CheckingRepo -> "Berechtigungen und devcontainer werden geprüft."
    is StartCodespaceState.PreparingRepo ->
        "Der Branch ${progress.repoFullName} wird mit dem Editor-Devcontainer ergänzt."

    is StartCodespaceState.CreatingCodespace -> "Der erste Start baut das Image auf (2–5 Min.)."
    is StartCodespaceState.WaitingForCodespace ->
        "Versuch ${progress.attempt} von ${progress.maxAttempts} – alle 5 Sekunden."

    is StartCodespaceState.ConnectingPort -> "Port ${13337} wird öffentlich geschaltet."
    is StartCodespaceState.PreparingExtensions ->
        "Kilo Code und Cline werden installiert (1–3 Min.). Der Port öffnet erst danach."

    is StartCodespaceState.WaitingForEditor -> "Der Editor wird geladen."
    is StartCodespaceState.Ready ->
        "Der Editor ist erreichbar. Der Port bleibt bis zum Verlassen öffentlich."

    is StartCodespaceState.Failed -> progress.message
    is StartCodespaceState.Cancelled -> "Der Vorgang wurde abgebrochen."
}

@Composable
private fun OptionsCard(state: StartUiState, viewModel: StartCodespaceViewModel) {
    val colors = AppTheme.colors
    AppCard {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Optionen",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
            )
            Spacer(Modifier.height(10.dp))

            if (state.machines.isNotEmpty()) {
                Text("Maschine", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                Spacer(Modifier.height(6.dp))
                state.machines.forEach { machine ->
                    OptionRow(
                        label = machine.displayName ?: machine.name,
                        detail = listOfNotNull(
                            machine.cores?.let { "$it Kerne" },
                            machine.memoryGb?.let { "$it GB" },
                        ).joinToString(" · "),
                        selected = machine.name == state.machine,
                        onClick = { viewModel.setMachine(machine.name) },
                    )
                }
                Spacer(Modifier.height(10.dp))
            } else {
                Text(
                    text = "Maschine: ${state.machine}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
            }

            Text("Region", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Geo.entries.forEach { geo ->
                    OptionPill(
                        label = geo.label,
                        selected = geo == state.geo,
                        onClick = { viewModel.setGeo(geo) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Text(
                text = "Idle-Timeout: ${state.idleTimeoutMinutes} Minuten",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60, 120).forEach { minutes ->
                    OptionPill(
                        label = "$minutes Min.",
                        selected = minutes == state.idleTimeoutMinutes,
                        onClick = { viewModel.setIdleTimeout(minutes) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.accent else colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun OptionPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SurfacePill(label = label, selected = selected, onClick = onClick)
}
