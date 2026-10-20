package com.codespacemanager.app.ui.screens.help

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codespacemanager.app.ui.theme.AppTheme

private data class HelpEntry(val title: String, val body: String)

private val helpEntries = listOf(
    HelpEntry(
        title = "Einrichtung",
        body = "Lege unter github.com/settings/developers eine OAuth-App an. Die Callback-URL lautet " +
            "com.codespacemanager.app://oauth/callback. Trage Client-ID und Client-Secret in den " +
            "Einstellungen ein oder hinterlege sie als Secrets GITHUB_CLIENT_ID und " +
            "GITHUB_CLIENT_SECRET im CI.",
    ),
    HelpEntry(
        title = "Codespace starten",
        body = "Der Editor läuft auf einem eigenen Branch codespace/editor. Der Default-Branch deines " +
            "Repositories bleibt unverändert – die Änderung kannst du als PR übernehmen.",
    ),
    HelpEntry(
        title = "Sicherheit: öffentlicher Port",
        body = "Solange der Editor geöffnet ist, läuft der code-server ohne Anmeldung und ist unter " +
            "einer anonymen Adresse erreichbar. Jeder mit dieser Adresse kann Code ausführen. " +
            "Teile die Adresse nicht. Beim Verlassen des Editors, beim Stoppen des Codespaces und " +
            "nach 15 Minuten Inaktivität wird der Port automatisch wieder privat geschaltet.",
    ),
    HelpEntry(
        title = "Erweiterungen",
        body = "Kilo Code, Cline und das GitHub-Dark-Theme werden in jedem Codespace installiert. " +
            "Trage deinen Provider-Key einmalig in der Erweiterung ein – er liegt im Codespace und " +
            "überlebt Neustarts.",
    ),
    HelpEntry(
        title = "Wenn etwas klemmt",
        body = "Der Browser-Fallback (Custom Tab) öffnet das native Codespaces-UI. dort lassen sich " +
            "Codespaces starten, stoppen und löschen. Prüfe außerdem dein Kontingent unter " +
            "github.com/settings/codespaces.",
    ),
    HelpEntry(
        title = "Bekannte Grenzen",
        body = "Nur github.com (keine GitHub Enterprise Server), kein Light-Modus, keine " +
            "Play-Store-Veröffentlichung, kein lokales Klonen vom Gerät.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val colors = AppTheme.colors
    Scaffold(
        containerColor = colors.canvas,
        topBar = {
            TopAppBar(
                title = { Text("Hilfe", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surfaceInset,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textPrimary,
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
            helpEntries.forEach { entry ->
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}
