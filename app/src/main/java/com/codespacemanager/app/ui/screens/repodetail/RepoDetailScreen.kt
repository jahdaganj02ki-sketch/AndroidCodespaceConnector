package com.codespacemanager.app.ui.screens.repodetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codespacemanager.app.domain.model.Codespace
import com.codespacemanager.app.domain.model.CodespaceState
import com.codespacemanager.app.platform.customtabs.CustomTabsHelper
import com.codespacemanager.app.ui.components.AppCard
import com.codespacemanager.app.ui.components.ErrorState
import com.codespacemanager.app.ui.components.LoadingState
import com.codespacemanager.app.ui.components.MonoLabel
import com.codespacemanager.app.ui.components.PrimaryButton
import com.codespacemanager.app.ui.components.SecondaryButton
import com.codespacemanager.app.ui.components.StatusChip
import com.codespacemanager.app.ui.components.StatusTone
import com.codespacemanager.app.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(
    repoFullName: String,
    onStart: () -> Unit,
    onOpenEditor: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: RepoDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val context = LocalContext.current

    LaunchedEffect(repoFullName) {
        viewModel.load(repoFullName)
    }

    Scaffold(
        containerColor = colors.canvas,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.repo?.name ?: repoFullName.substringAfter('/'),
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
                .padding(padding),
        ) {
            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = { viewModel.load(repoFullName) },
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Column {
                            MonoLabel(
                                text = "${state.repo?.fullName ?: repoFullName} · " +
                                    "Branch ${state.repo?.defaultBranch ?: "main"}",
                            )
                            Spacer(Modifier.height(12.dp))
                            PrimaryButton(
                                text = "Codespace starten",
                                onClick = onStart,
                                icon = Icons.Outlined.PlayArrow,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Der Editor wird auf dem Branch codespace/editor vorbereitet. " +
                                    "Der Default-Branch bleibt unverändert.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Codespaces dieses Repos",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    items(state.codespaces, key = { it.name }) { codespace ->
                        CodespaceCard(
                            codespace = codespace,
                            onOpen = { onOpenEditor(codespace.name) },
                            onStop = { viewModel.stop(codespace) },
                            onDelete = { viewModel.delete(codespace) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodespaceCard(
    codespace: Codespace,
    onOpen: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AppTheme.colors
    AppCard {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = codespace.displayLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(
                    label = codespace.state.raw,
                    tone = when (codespace.state) {
                        CodespaceState.AVAILABLE -> StatusTone.ACTIVE
                        CodespaceState.SHUTDOWN -> StatusTone.MUTED
                        else -> StatusTone.NEUTRAL
                    },
                )
            }
            Spacer(Modifier.height(4.dp))
            MonoLabel(
                text = listOfNotNull(codespace.machine, codespace.location).joinToString(" · "),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    text = "Öffnen",
                    onClick = onOpen,
                    enabled = codespace.state == CodespaceState.AVAILABLE,
                )
                SecondaryButton(text = "Stoppen", onClick = onStop)
                SecondaryButton(text = "Löschen", onClick = onDelete)
            }
        }
    }
}
