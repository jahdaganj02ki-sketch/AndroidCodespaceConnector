package com.codespacemanager.app.ui.screens.accounts

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.codespacemanager.app.domain.model.Account
import com.codespacemanager.app.platform.customtabs.CustomTabsHelper
import com.codespacemanager.app.ui.components.AppCard
import com.codespacemanager.app.ui.components.EmptyState
import com.codespacemanager.app.ui.components.LoadingState
import com.codespacemanager.app.ui.components.MonoLabel
import com.codespacemanager.app.ui.components.PrimaryButton
import com.codespacemanager.app.ui.components.SecondaryButton
import com.codespacemanager.app.ui.components.StatusChip
import com.codespacemanager.app.ui.components.StatusTone
import com.codespacemanager.app.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = AppTheme.colors
    var pendingRemoval by remember { mutableStateOf<Account?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AccountsEvent.OpenUrl -> CustomTabsHelper.open(context, event.url)
                is AccountsEvent.Message -> Unit
            }
        }
    }

    Scaffold(
        containerColor = colors.canvas,
        topBar = {
            TopAppBar(
                title = { Text("Konten", style = MaterialTheme.typography.titleLarge) },
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
            Text(
                text = "Mehrere GitHub-Konten können parallel verbunden sein. Beim Anmelden " +
                    "zeigt GitHub automatisch die Kontoauswahl.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(16.dp),
            )

            if (state.loading) {
                LoadingState()
            } else if (state.accounts.isEmpty()) {
                EmptyState(
                    title = "Noch kein Konto verbunden",
                    message = "Melde dich mit deinem GitHub-Konto an, um Codespaces zu verwalten.",
                    icon = Icons.Outlined.Add,
                    action = { PrimaryButton(text = "Konto hinzufügen", onClick = viewModel::addAccount) },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.accounts, key = { it.id }) { account ->
                        AccountRow(
                            account = account,
                            isActive = account.login.equals(state.activeLogin, ignoreCase = true),
                            onSwitch = { viewModel.switchAccount(account) },
                            onRemove = { pendingRemoval = account },
                        )
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton(
                            text = "Konto hinzufügen",
                            onClick = viewModel::addAccount,
                            icon = Icons.Outlined.Add,
                        )
                    }
                }
            }
        }
    }

    pendingRemoval?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            containerColor = colors.surfaceRaised,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text("Konto entfernen?") },
            text = {
                Text(
                    "Das Token von ${account.login} wird gelöscht. " +
                        "Der Codespace selbst bleibt bestehen.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeAccount(account)
                    pendingRemoval = null
                }) {
                    Text("Entfernen", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text("Abbrechen", color = colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun AccountRow(
    account: Account,
    isActive: Boolean,
    onSwitch: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = AppTheme.colors
    AppCard {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(account.avatarUrl, account.login)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.login,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                MonoLabel(
                    text = if (account.scopes.isEmpty()) {
                        "repo · codespace · read:org"
                    } else {
                        account.scopes.joinToString(" · ")
                    },
                )
            }
            if (isActive) {
                StatusChip(label = "Aktiv", tone = StatusTone.ACTIVE)
            } else {
                SecondaryButton(text = "Wechseln", onClick = onSwitch)
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Konto entfernen",
                    tint = colors.textMuted,
                )
            }
        }
    }
}

@Composable
fun Avatar(url: String, login: String, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.surfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = login,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else {
            Text(
                text = login.take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
            )
        }
    }
}
