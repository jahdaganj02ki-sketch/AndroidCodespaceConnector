package com.codespacemanager.app.ui.screens.editor

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codespacemanager.app.domain.usecase.CodespaceUrls
import com.codespacemanager.app.platform.customtabs.CustomTabsHelper
import com.codespacemanager.app.platform.webview.CodeServerWebView
import com.codespacemanager.app.platform.webview.MutableWebViewHolder
import com.codespacemanager.app.ui.components.PrimaryButton
import com.codespacemanager.app.ui.components.SecondaryButton
import com.codespacemanager.app.ui.components.StatusChip
import com.codespacemanager.app.ui.components.StatusTone
import com.codespacemanager.app.ui.theme.AppTheme
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    codespaceName: String,
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val context = LocalContext.current
    val webViewHolder = remember { MutableWebViewHolder() }
    val httpClient = remember {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    val browseUrl = remember(codespaceName) {
        CodespaceUrls.browseUrlWithFolder(codespaceName, state.port, null)
    }

    LaunchedEffect(codespaceName) {
        viewModel.load(codespaceName)
    }

    Scaffold(
        containerColor = colors.canvas,
        topBar = {
            EditorTopBar(
                codespaceName = codespaceName,
                portPublic = state.portPublic,
                onBack = onBack,
                onToggleKeyboard = {
                    webViewHolder.view?.let { view ->
                        val imm = context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                        imm?.hideSoftInputFromWindow(view.windowToken, 0)
                    }
                },
                onTogglePort = { viewModel.setPortPublic(!state.portPublic) },
                onOpenBrowser = {
                    CustomTabsHelper.open(context, CodespaceUrls.codespaceWebUi(codespaceName))
                },
                onReload = { webViewHolder.view?.reload() },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            CodeServerWebView(
                url = browseUrl,
                codespaceName = codespaceName,
                httpClient = httpClient,
                onExternalUrl = { url -> CustomTabsHelper.open(context, url) },
                onError = { viewModel.onConnectionError() },
                onPageLoaded = { viewModel.onPageLoaded() },
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { viewModel.onInteraction() },
                webViewHolder = webViewHolder,
            )

            state.recovery?.let { reason ->
                RecoveryOverlay(
                    reason = reason,
                    onReopen = viewModel::reopenPort,
                    onRestart = viewModel::restartCodespace,
                    onBrowser = {
                        CustomTabsHelper.open(context, CodespaceUrls.codespaceWebUi(codespaceName))
                    },
                )
            }
        }
    }

    if (state.showAgentHint) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissAgentHint() },
            sheetState = sheetState,
            containerColor = colors.surfaceRaised,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Kilo Code & Cline sind bereit",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Trage deinen Provider-Key (Anthropic, OpenRouter oder dein Kilo-Konto) " +
                        "in der Erweiterung ein. Der Key liegt danach im SecretStorage des " +
                        "Codespaces und überlebt Neustarts. Die App speichert keine Keys.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(20.dp))
                PrimaryButton(text = "Verstanden", onClick = viewModel::dismissAgentHint)
            }
        }
    }
}

@Composable
private fun EditorTopBar(
    codespaceName: String,
    portPublic: Boolean,
    onBack: () -> Unit,
    onToggleKeyboard: () -> Unit,
    onTogglePort: () -> Unit,
    onOpenBrowser: () -> Unit,
    onReload: () -> Unit,
) {
    val colors = AppTheme.colors
    Surface(color = colors.surfaceInset) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = "Zurück",
                    tint = colors.textPrimary,
                )
            }
            Text(
                text = codespaceName,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StatusChip(
                label = if (portPublic) "Port öffentlich" else "Port privat",
                tone = if (portPublic) StatusTone.ACTIVE else StatusTone.MUTED,
            )
            IconButton(onClick = onToggleKeyboard) {
                Icon(
                    imageVector = Icons.Outlined.Keyboard,
                    contentDescription = "Tastatur ausblenden",
                    tint = colors.textSecondary,
                )
            }
            IconButton(onClick = onTogglePort) {
                Icon(
                    imageVector = if (portPublic) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                    contentDescription = if (portPublic) "Port privat schalten" else "Port öffentlich schalten",
                    tint = colors.textSecondary,
                )
            }
            IconButton(onClick = onOpenBrowser) {
                Icon(
                    imageVector = Icons.Outlined.OpenInBrowser,
                    contentDescription = "Im Browser öffnen",
                    tint = colors.textSecondary,
                )
            }
            IconButton(onClick = onReload) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Neu laden",
                    tint = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun RecoveryOverlay(
    reason: RecoveryReason,
    onReopen: () -> Unit,
    onRestart: () -> Unit,
    onBrowser: () -> Unit,
) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
            modifier = Modifier.padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (reason == RecoveryReason.CODESPACE_STOPPED) {
                        "Codespace gestoppt"
                    } else {
                        "Verbindung zum Editor verloren"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (reason == RecoveryReason.CODESPACE_STOPPED) {
                        "Der Codespace läuft nicht. Starte ihn neu, um weiterzuarbeiten."
                    } else {
                        "Der Port wurde geschlossen oder ist nicht mehr erreichbar. " +
                            "Öffne ihn erneut, um weiterzuarbeiten."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(20.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PrimaryButton(text = "Port wieder öffnen", onClick = onReopen)
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(text = "Codespace neu starten", onClick = onRestart)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onBrowser) {
                        Text("Im Browser öffnen", color = colors.textSecondary)
                    }
                }
            }
        }
    }
}
