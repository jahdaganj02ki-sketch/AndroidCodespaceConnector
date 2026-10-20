package com.codespacemanager.app.ui.screens.codespaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codespacemanager.app.domain.model.Codespace
import com.codespacemanager.app.domain.model.CodespaceState
import com.codespacemanager.app.domain.usecase.QuotaEstimator
import com.codespacemanager.app.platform.customtabs.CustomTabsHelper
import com.codespacemanager.app.ui.components.AppCard
import com.codespacemanager.app.ui.components.EmptyState
import com.codespacemanager.app.ui.components.ErrorState
import com.codespacemanager.app.ui.components.LoadingState
import com.codespacemanager.app.ui.components.MonoLabel
import com.codespacemanager.app.ui.components.SecondaryButton
import com.codespacemanager.app.ui.components.StatusChip
import com.codespacemanager.app.ui.components.StatusTone
import com.codespacemanager.app.ui.theme.AppTheme
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodespacesScreen(
    onOpenRepo: (String) -> Unit,
    onOpenEditor: (String) -> Unit,
    viewModel: CodespacesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val context = LocalContext.current
    var renameTarget by remember { mutableStateOf<Codespace?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CodespacesEvent.Message -> Unit
                is CodespacesEvent.OpenEditor -> onOpenEditor(event.codespaceName)
            }
        }
    }

    Scaffold(
        containerColor = colors.canvas,
        topBar = {
            TopAppBar(
                title = { Text("Codespaces", style = MaterialTheme.typography.titleLarge) },
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
                .padding(padding),
        ) {
            QuotaHeader(
                estimate = state.coreHoursEstimate,
                account = state.activeAccount,
            )

            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::refresh,
                )

                state.codespaces.isEmpty() -> EmptyState(
                    title = "Keine Codespaces",
                    message = "Starte einen Codespace über ein Repository, um hier zu landen.",
                    icon = Icons.Outlined.Code,
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.codespaces, key = { it.name }) { codespace ->
                        CodespaceRow(
                            codespace = codespace,
                            onOpen = {
                                if (codespace.state == CodespaceState.AVAILABLE) {
                                    onOpenEditor(codespace.name)
                                } else {
                                    CustomTabsHelper.open(
                                        context,
                                        "https://github.com/codespaces/${codespace.name}",
                                    )
                                }
                            },
                            onRepo = { onOpenRepo(codespace.repositoryFullName) },
                            onStop = { viewModel.stop(codespace) },
                            onDelete = { viewModel.delete(codespace) },
                            onRename = { renameTarget = codespace },
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { codespace ->
        RenameDialog(
            initial = codespace.displayName ?: codespace.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                viewModel.rename(codespace, newName)
                renameTarget = null
            },
        )
    }
}

@Composable
private fun QuotaHeader(estimate: Double, account: String?) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = "Kontingent-Schätzung: ${QuotaEstimator.format(estimate)}",
            style = MaterialTheme.typography.labelLarge,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = (account?.let { "Konto $it · " } ?: "") +
                "Schätzung aus Maschine und letzter Nutzung – keine Abrechnung.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun CodespaceRow(
    codespace: Codespace,
    onOpen: () -> Unit,
    onRepo: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
) {
    val colors = AppTheme.colors
    AppCard {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = codespace.displayLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    MonoLabel(
                        text = codespace.repositoryFullName.ifBlank { codespace.name },
                        color = colors.textMuted,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusChip(
                    label = codespace.state.raw,
                    tone = when (codespace.state) {
                        CodespaceState.AVAILABLE -> StatusTone.ACTIVE
                        CodespaceState.SHUTDOWN -> StatusTone.MUTED
                        else -> StatusTone.NEUTRAL
                    },
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            MonoLabel(
                text = listOfNotNull(
                    codespace.machine,
                    codespace.location,
                    codespace.gitStatus?.ref,
                ).joinToString(" · "),
                color = colors.textSecondary,
            )
            if (codespace.gitStatus != null) {
                Spacer(Modifier.height(2.dp))
                MonoLabel(
                    text = "git: ${codespace.gitStatus.ref ?: "–"} " +
                        "(${codespace.gitStatus.ahead}↑ ${codespace.gitStatus.behind}↓)",
                    color = if (codespace.gitStatus.hasUncommittedChanges ||
                        codespace.gitStatus.hasUnpushedCommits
                    ) {
                        colors.accent
                    } else {
                        colors.textMuted
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(text = "Öffnen", onClick = onOpen)
                SecondaryButton(text = "Repo", onClick = onRepo)
                SecondaryButton(
                    text = if (codespace.state == CodespaceState.SHUTDOWN) "Starten" else "Stoppen",
                    onClick = onStop,
                )
                SecondaryButton(text = "Umbenennen", onClick = onRename)
                SecondaryButton(text = "Löschen", onClick = onDelete)
            }
        }
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = AppTheme.colors
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceRaised,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = { Text("Codespace umbenennen") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }) {
                Text("Speichern", color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen", color = colors.textSecondary)
            }
        },
    )
}
