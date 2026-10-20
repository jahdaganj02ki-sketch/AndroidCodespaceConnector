package com.codespacemanager.app.ui.screens.repos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codespacemanager.app.domain.model.Repo
import com.codespacemanager.app.domain.model.RepoTemplate
import com.codespacemanager.app.domain.usecase.RepoNameValidator
import com.codespacemanager.app.ui.components.AppCard
import com.codespacemanager.app.ui.components.EmptyState
import com.codespacemanager.app.ui.components.ErrorState
import com.codespacemanager.app.ui.components.LoadingState
import com.codespacemanager.app.ui.components.MonoLabel
import com.codespacemanager.app.ui.components.PrimaryButton
import com.codespacemanager.app.ui.components.StatusChip
import com.codespacemanager.app.ui.components.StatusTone
import com.codespacemanager.app.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReposScreen(
    onOpenRepo: (String) -> Unit,
    viewModel: ReposViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    var showCreateDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ReposEvent.Created -> {
                    showCreateDialog = false
                    onOpenRepo(event.repo.fullName)
                }

                is ReposEvent.Message -> Unit
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                val total = listState.layoutInfo.totalItemsCount
                if (lastVisible != null && total > 0 && lastVisible >= total - 3) {
                    viewModel.loadMore()
                }
            }
    }

    Scaffold(
        containerColor = colors.canvas,
        topBar = {
            TopAppBar(
                title = { Text("Repos", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surfaceInset,
                    titleContentColor = colors.textPrimary,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = colors.accent,
                contentColor = colors.onAccent,
                shape = androidx.compose.foundation.shape.CircleShape,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Neues Repository")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Repos durchsuchen") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )

            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::refresh,
                )

                state.filtered.isEmpty() -> EmptyState(
                    title = "Keine Repositories gefunden",
                    message = "Lege ein neues Repository an oder passe die Suche an.",
                    icon = Icons.Outlined.Search,
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.filtered, key = { it.id }) { repo ->
                        RepoRow(repo = repo, onClick = { onOpenRepo(repo.fullName) })
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateRepoDialog(
            organizations = state.organizations,
            creating = state.creating,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, isPrivate, owner, template ->
                viewModel.createRepo(name, isPrivate, owner, template)
            },
        )
    }
}

@Composable
private fun RepoRow(repo: Repo, onClick: () -> Unit) {
    val colors = AppTheme.colors
    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repo.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                MonoLabel(
                    text = "${repo.fullName} · ${repo.defaultBranch}",
                    color = colors.textMuted,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(
                label = if (repo.isPrivate) "privat" else "öffentlich",
                tone = if (repo.isOrganization) StatusTone.NEUTRAL else StatusTone.MUTED,
            )
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CreateRepoDialog(
    organizations: List<String>,
    creating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, Boolean, String?, RepoTemplate) -> Unit,
) {
    val colors = AppTheme.colors
    var name by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(true) }
    var owner by remember { mutableStateOf<String?>(null) }
    var template by remember { mutableStateOf(RepoTemplate.STANDARD) }

    val nameError = RepoNameValidator.validate(name)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceRaised,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = { Text("Neues Repository") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    isError = name.isNotEmpty() && nameError != null,
                    supportingText = if (name.isNotEmpty() && nameError != null) {
                        { Text(nameError.orEmpty()) }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Privat",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                }
                if (organizations.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Besitzer",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    Column {
                        OwnerOption("Eigenes Konto", owner == null) { owner = null }
                        organizations.forEach { org ->
                            OwnerOption(org, owner == org) { owner = org }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Vorlage",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(6.dp))
                Column {
                    RepoTemplate.entries.forEach { entry ->
                        OwnerOption(
                            label = "${entry.label} – ${entry.description}",
                            selected = template == entry,
                        ) { template = entry }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim(), isPrivate, owner, template) },
                enabled = nameError == null && !creating,
            ) {
                Text("Anlegen", color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen", color = colors.textSecondary)
            }
        },
    )
}

@Composable
private fun OwnerOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(
                    color = if (selected) colors.accent else colors.surface,
                    shape = androidx.compose.foundation.shape.CircleShape,
                )
                .border(
                    width = 1.dp,
                    color = if (selected) colors.accent else colors.borderStrong,
                    shape = androidx.compose.foundation.shape.CircleShape,
                ),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.textPrimary else colors.textSecondary,
        )
    }
}
