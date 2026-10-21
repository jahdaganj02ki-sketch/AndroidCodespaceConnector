package com.codespacemanager.app.ui.screens.login

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codespacemanager.app.domain.model.AuthStatus
import com.codespacemanager.app.platform.auth.SignInProvider
import com.codespacemanager.app.platform.customtabs.CustomTabsHelper
import com.codespacemanager.app.ui.components.AppCard
import com.codespacemanager.app.ui.components.MonoLabel
import com.codespacemanager.app.ui.components.PrimaryButton
import com.codespacemanager.app.ui.components.SecondaryButton
import com.codespacemanager.app.ui.theme.AppTheme

/**
 * Anmeldescreen: „Mit GitHub anmelden" / „Mit Google anmelden".
 * Beide Wege delegieren an GitHubs eigene Anmeldeseite (Custom Tab).
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = AppTheme.colors

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.OpenUrl -> CustomTabsHelper.open(context, event.url)
                is LoginEvent.NeedsConfiguration -> Unit
            }
        }
    }

    LaunchedEffect(state.status) {
        if (state.status is AuthStatus.Success) {
            viewModel.clearStatus()
            onLoggedIn()
        }
    }

    val busy = state.status == AuthStatus.Authenticating

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Code,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Codespace Manager",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Melde dich an, um deine Codespaces zu verwalten.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        PrimaryButton(
            text = "Mit GitHub anmelden",
            onClick = { viewModel.signIn(SignInProvider.GITHUB) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        )
        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            text = "Mit Google anmelden",
            onClick = { viewModel.signIn(SignInProvider.GOOGLE) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "GitHub öffnet sich im Browser. Dort kannst du dich mit deinem GitHub- " +
                "oder deinem Google-Konto anmelden.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )

        if (state.hasClientId && !state.hasClientSecret) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Es fehlt ein Client-Secret – die Anmeldung bricht beim Token-Tausch ab. " +
                    "Bitte unten konfigurieren.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }

        if (busy) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = colors.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Anmeldung läuft …",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }

        val error = state.status as? AuthStatus.Error
        if (error != null) {
            Spacer(Modifier.height(20.dp))
            AppCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Anmeldung fehlgeschlagen",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = error.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(text = "Erneut versuchen", onClick = { viewModel.retry() })
                        SecondaryButton(text = "Schließen", onClick = { viewModel.dismissError() })
                    }
                }
            }
        }

        state.notice?.let { notice ->
            Spacer(Modifier.height(16.dp))
            Text(
                text = notice,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(28.dp))
        OAuthConfigSection(
            expanded = state.configExpanded,
            clientId = state.clientId,
            clientSecret = state.clientSecret,
            onToggle = viewModel::toggleConfig,
            onClientIdChange = viewModel::setClientId,
            onClientSecretChange = viewModel::setClientSecret,
            onSave = viewModel::saveOauthConfig,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OAuthConfigSection(
    expanded: Boolean,
    clientId: String,
    clientSecret: String,
    onToggle: () -> Unit,
    onClientIdChange: (String) -> Unit,
    onClientSecretChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val colors = AppTheme.colors
    AppCard {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "OAuth-App konfigurieren",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Abschnitt einklappen" else "Abschnitt ausklappen",
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = clientId,
                    onValueChange = onClientIdChange,
                    label = { Text("Client-ID") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = onClientSecretChange,
                    label = { Text("Client-Secret") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = "Speichern",
                    onClick = onSave,
                    enabled = clientId.isNotBlank() && clientSecret.isNotBlank(),
                )
                Spacer(Modifier.height(10.dp))
                MonoLabel("Callback: com.codespacemanager.app://oauth/callback")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Die Zugangsdaten werden lokal im DataStore gespeichert und überschreiben " +
                        "die im Build hinterlegten Werte.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }
    }
}
