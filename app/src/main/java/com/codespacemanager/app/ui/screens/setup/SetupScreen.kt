package com.codespacemanager.app.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codespacemanager.app.ui.components.PrimaryButton
import com.codespacemanager.app.ui.components.SectionHeader
import com.codespacemanager.app.ui.theme.AppTheme

@Composable
fun SetupScreen(
    onSaved: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = "Codespace Manager",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Für die GitHub-Anmeldung wird eine OAuth-App benötigt. Lege sie unter " +
                "github.com/settings/developers → OAuth Apps an und trage Client-ID und " +
                "Client-Secret hier ein.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Callback-URL: com.codespacemanager.app://oauth/callback",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(24.dp))

        SectionHeader(title = "Zugangsdaten")
        OutlinedTextField(
            value = state.clientId,
            onValueChange = viewModel::setClientId,
            label = { Text("Client-ID") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.clientSecret,
            onValueChange = viewModel::setClientSecret,
            label = { Text("Client-Secret") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = "Speichern",
            onClick = { viewModel.save(onSaved) },
            enabled = state.clientId.isNotBlank() && state.clientSecret.isNotBlank(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Die Zugangsdaten werden ausschließlich lokal verschlüsselt gespeichert. " +
                "Alternativ kann der Build über die Secrets GITHUB_CLIENT_ID und " +
                "GITHUB_CLIENT_SECRET konfiguriert werden.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
    }
}
