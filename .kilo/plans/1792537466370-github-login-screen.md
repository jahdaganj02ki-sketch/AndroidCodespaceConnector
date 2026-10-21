# Anmeldung: GitHub-Login als erster Bildschirm (mit GitHub- und Google-Option)

## Ziel

Die Anmeldung ist heute an zwei Stellen verteilt: als **SetupScreen** (Client-ID/Secret) ist sie der allererste Bildschirm der App, die eigentliche GitHub-Anmeldung steckt in „Konten" → „Konto hinzufügen".

Zielbild: Beim Start steht die Anmeldung mit **„Mit GitHub anmelden"** und **„Mit Google anmelden"** im Vordergrund — wie auf github.com. Die OAuth-App wird mit dem Build ausgeliefert, ein Setup-Schritt entfällt für reguläre Builds.

## Getroffene Entscheidungen

| # | Entscheidung | Begründung |
|---|---|---|
| 1 | Beide Buttons delegieren an **GitHubs eigene Anmeldeseite** (Custom Tab). Kein Google-SDK in der App. | GitHub bietet „Continue with Google/Apple" erst auf *seiner* Seite an. Ein Google-ID-Token lässt sich nicht in ein GitHub-Token tauschen; ein natives Google-Sign-in liefert also keinen GitHub-Zugriff und wäre für die App nutzlos. |
| 2 | Natives Google-Sign-in bleibt als **spätere, optionale Erweiterung** vorgesehen (Feature-Flag/Naht), wird jetzt **nicht** umgesetzt. | Vom Nutzer so entschieden. Die Naht ist in Aufgabe 2/3 so geschnitten, dass sie später ohne Umbau ergänzbar ist. |
| 3 | Die OAuth-App wird **fest mit ausgeliefert** (CI-Secrets → `BuildConfig`). Der SetupScreen **entfällt** als erster Bildschirm. | Vom Nutzer so entschieden. Konsequenz: Client-ID **und** Client-Secret liegen im APK (GitHub verlangt das Secret beim Web-Flow; RFC 8252 stuft Secrets in nativen Apps ohnehin als öffentlich ein). |
| 4 | Der „Mit Google anmelden"-Button öffnet die Authorize-URL **ohne** `prompt=select_account`; der „Mit GitHub anmelden"-Button **mit**. | Ohne `prompt` blendet GitHub seine volle Anmeldeseite inkl. „Continue with Google/Apple" ein — exakt github.com/login. Mit `prompt=select_account` erscheint der Kontoauswahl-Dialog. |

## Verifizierte technische Grundlage

- **GitHubs „Continue with Google" existiert.** GA seit 2025-07-15 ([Changelog](https://github.blog/changelog/2025-07-15-social-login-with-google-is-now-generally-available/)), Apple seit 2025-10-07. Die Buttons werden **clientseitig** gerendert und sind im Roh-HTML von `github.com/login` nicht sichtbar. → Muss auf dem Gerät verifiziert werden (Schritt V4).
- **`/login/oauth/authorize` kennt keinen Provider-Parameter.** Dokumentiert sind nur `client_id`, `redirect_uri`, `scope`, `state`, `code_challenge`, `code_challenge_method`, `allow_signup`, `login`, `prompt`. `https://github.com/login/google` und `https://github.com/sessions/google` liefern **404** → keine Deep-Link-Schnittstelle, die App kann „Continue with Google" nicht direkt ansteuern.
- **Die Authorize-URL rendert GitHubs echtes Anmeldeformular**, wenn der Browser nicht bei GitHub angemeldet ist (mit Dummy-`client_id` geprüft) — der bestehende Custom-Tab-Flow zeigt also bereits heute die GitHub-Seite.
- **`client_secret` bleibt beim Web-Flow Pflicht** (GitHub-Doku: „Required"; nur Device-Flow und Refresh erlauben es wegzulassen). Changelog 2025-07-14: *„GitHub does not distinguish between public and confidential clients."*
- **Callback-URLs:** Für OAuth-Apps, die **nach dem 03.08.2026** angelegt wurden, ist Wildcard-Matching standardmäßig aus; die `redirect_uri` muss **exakt** `com.codespacemanager.app://oauth/callback` sein. Ältere Apps haben Wildcard-Matching aktiviert.
- **PKCE ist bereits im Code** (`OAuth.authorizeUrl`, `code_challenge_method=S256`) — Voraussetzung dafür, dass ein im APKliegendes Secret allein nichts ausrichtet.
- **Offene Baustelle (nicht Teil dieses Plans):** GitHub bietet ablaufende Tokens (8 h Access + 6 Monate Refresh, Scope `offline_access`). Die App nutzt heute unbefristete Tokens.

## Betroffene Dateien

**Neu**
- `app/src/main/java/com/codespacemanager/app/ui/screens/login/LoginScreen.kt`
- `app/src/main/java/com/codespacemanager/app/ui/screens/login/LoginViewModel.kt`
- `app/src/test/java/com/codespacemanager/app/platform/auth/OAuthTest.kt`

**Geändert**
- `app/src/main/java/com/codespacemanager/app/platform/auth/OAuth.kt` — `SignInProvider`-Enum, `prompt`-Logik
- `app/src/main/java/com/codespacemanager/app/platform/auth/OAuthSession.kt` — Persistenz über ProzessTod
- `app/src/main/java/com/codespacemanager/app/data/repository/AuthCoordinator.kt` — `authorizeUrl(provider)`
- `app/src/main/java/com/codespacemanager/app/data/local/datastore/SettingsStore.kt` — neue Keys, `setupCompleted` entfällt
- `app/src/main/java/com/codespacemanager/app/domain/gateway/SettingsGateway.kt` — `AppSettings`: `setupCompleted`/`hasCredentials` entfallen, Pending-OAuth-Felder kommen
- `app/src/main/java/com/codespacemanager/app/ui/MainViewModel.kt` — Gate von „Credentials" auf „Konto vorhanden" umstellen
- `app/src/main/java/com/codespacemanager/app/ui/navigation/AppNavigation.kt` — Route `LOGIN`, dynamische Startdestination, Bottom-Leiste
- `app/src/main/java/com/codespacemanager/app/ui/screens/accounts/AccountsViewModel.kt` + `AccountsScreen.kt` — „Konto hinzufügen" → Route `LOGIN`
- `app/src/main/java/com/codespacemanager/app/ui/screens/settings/SettingsScreen.kt` — Beschriftung der OAuth-Karte
- `app/src/main/java/com/codespacemanager/app/ui/components/Components.kt` — ggf. `OutlineButton`/Icon-Slot
- `README.md`, `docs/setup.md`, `docs/api-reference.md`

**Gelöscht**
- `app/src/main/java/com/codespacemanager/app/ui/screens/setup/SetupScreen.kt`
- `app/src/main/java/com/codespacemanager/app/ui/screens/setup/SetupViewModel.kt`
- `Routes.SETUP` (kein Nav-Eintrag mehr)

## Umsetzung

### 1. OAuth-URL um die Provider-Variante erweitern
`platform/auth/OAuth.kt`:
```kotlin
enum class SignInProvider { GITHUB, GOOGLE }

fun authorizeUrl(
    clientId: String,
    request: OAuthRequest,
    provider: SignInProvider = SignInProvider.GITHUB,
    login: String? = null,
): String {
    val params = linkedMapOf(
        "client_id" to clientId,
        "redirect_uri" to request.redirectUri,
        "scope" to SCOPES,
        "state" to request.state,
        "code_challenge" to Pkce.codeChallenge(request.codeVerifier),
        "code_challenge_method" to "S256",
    )
    if (provider == SignInProvider.GITHUB) params["prompt"] = "select_account"
    // GOOGLE: kein "prompt" -> GitHub zeigt die volle Anmeldeseite mit
    // "Continue with Google" / "Continue with Apple".
    if (!login.isNullOrBlank()) params["login"] = login
    ...
}
```
`allow_signup` **nicht** setzen (Default `true`) → Nutzer ohne GitHub-Konto können sich wie auf github.com über Google anmelden.
`AuthCoordinator.authorizeUrl(provider)` reicht den Provider durch; Rückgabe `null` bleibt, wenn keine Client-ID hinterlegt ist.

### 2. LoginScreen + LoginViewModel (neu)
`LoginViewModel`:
- `signIn(provider: SignInProvider)`: baut die URL über `AuthCoordinator`, emittiert `LoginEvent.OpenUrl(url)` oder `LoginEvent.NeedsConfiguration`.
- `state: StateFlow<LoginUiState>` mit `hasClientId` (aus `CredentialsProvider.hasCredentials()` — BuildConfig-bewusst) und den Feldern `clientId`/`clientSecret` für den Konfigurationsabschnitt.
- `saveOauthConfig()`: schreibt `clientIdOverride`/`clientSecretOverride` in den DataStore (Logik aus `SetupViewModel` übernehmen, dort `setupCompleted = true` **entfällt**).
- `dismissError()`.

`LoginScreen` (Layout, in Anlehnung an `SetupScreen`/`EmptyState`/`PrimaryButton`):
- Titel „Codespace Manager", Untertitel „Melde dich an, um deine Codespaces zu verwalten."
- `PrimaryButton` „Mit GitHub anmelden" → `signIn(GITHUB)`
- `SecondaryButton` „Mit Google anmelden" → `signIn(GOOGLE)`
- Hinweistext: „GitHub öffnet sich im Browser. Dort kannst du dich mit deinem GitHub- oder deinem Google-Konto anmelden."
- Fortschritt, wenn `LoginState.Authenticating`; Fehlerkarte, wenn `LoginState.Error` (mit „Erneut versuchen").
- **Abschnitt „OAuth-App konfigurieren"** (klappbar, per Default **ausgeklappt, wenn keine Client-ID hinterlegt ist**): Client-ID-/Secret-Felder + „Speichern". → Ersetzt den SetupScreen vollständig und verhindert eine Sackgasse bei Builds ohne Secrets.
- Events per `SharedFlow`; der Screen öffnet die URL über `CustomTabsHelper.open(context, url)` (gleiches Muster wie `AccountsScreen`).

### 3. Gate umstellen: „Konto vorhanden" statt „Credentials vorhanden"
`MainViewModel`:
```kotlin
val accounts: StateFlow<List<Account>?> = accountRepository.accounts
    .map { it as List<Account>? }
    .stateIn(viewModelScope, SharingStarted.Eagerly, null)   // null = noch nicht geladen
```
`hasCredentials` entfällt.

`AppRoot` (`ui/navigation/AppNavigation.kt`):
- `accounts == null` → `LoadingState()` (Kurzstart mit Kontenprüfung, **kein** Flackern des Login-Screens).
- `accounts.isEmpty()` → `LoginScreen(...)` **ohne** Scaffold/Bottom-Leiste.
- sonst → `Scaffold` + `NavHost` wie bisher, zusätzlich `composable(Routes.LOGIN)` für „Konto hinzufügen" aus den Konten.
- Nach erfolgreichem Login im **Gate-Fall** wechselt der Zustand automatisch (Konto existiert nun) → kein Navigations-Overhead.
- Nach erfolgreichem Login in der **Route** (innerhalb der App): `onLoggedIn` → `navController.navigate(Routes.CODESPACES) { popUpTo(Routes.LOGIN) { inclusive = true } }`.
- Wird das **letzte** Konto in „Konten" entfernt: `LaunchedEffect` auf `accounts` → Navigation zu `Routes.LOGIN`. Achtung: `accounts` ist beim Start `null` → der Effekt darf beim ersten Lauf **nicht** navigieren (Bedingung: Übergang von „hatte Konto" zu „kein Konto").
- `AppBottomBar` blendet die Leiste auf `Routes.LOGIN` bereits aus (Route ist kein Bottom-Item) — die `Scaffold`-Padding unten ist dort trotzdem reserviert; optional auf Bottom-Routen beschränken.

### 4. SetupScreen entfernen
- `SetupScreen.kt` und `SetupViewModel.kt` löschen, `Routes.SETUP` und den Nav-Eintrag entfernen.
- Die Credential-Felder aus `SetupViewModel` wandern in `LoginViewModel` (Aufgabe 2).
- `AppSettings.setupCompleted` und `AppSettings.hasCredentials` aus `SettingsGateway.kt` entfernen, `KEY_SETUP_COMPLETED` aus `SettingsStore` entfernen. Der alte DataStore-Eintrag bleibt ungenutzt im File liegen — **keine Migration nötig**.
- `CredentialsProvider.hasCredentials()` (`data/local/CredentialsProvider.kt:23`) bleibt die maßgebliche Quelle (sie berücksichtigt `BuildConfig` **und** den DataStore-Override).

### 5. OAuthSession über den ProzessTod hinweg persistieren
`OAuthSession` hält den laufenden Flow nur im Arbeitsspeicher (`pending`). Wird der Prozess während des Browser-Rundgangs beendet, meldet `AuthCoordinator.handleCallback` „Kein laufender Login gefunden" — mit der Anmeldung als erstem Bildschirm trifft das deutlich häufiger.
- `AppSettings` erhält `pendingOAuthState: String = ""` und `pendingOAuthVerifier: String = ""`.
- `SettingsStore`: zwei neue `stringPreferencesKey`s, in `toSettings`/`applySettings` ergänzen.
- `OAuthSession.begin/consume` werden `suspend` und lesen/schreiben den DataStore; `consume()` löscht die Felder.
- `AuthCoordinator.authorizeUrl`/`handleCallback` sind bereits `suspend` → keine Signaturbruchstelle. `peek()`/`clear()` prüfen (aktuell keine Aufrufer) und bei Bedarf anpassen.

### 6. Konten-Screen auf die Login-Route umstellen
- `AccountsViewModel.addAccount()` öffnet nicht mehr direkt den Custom Tab, sondern die Route `Routes.LOGIN` navigieren (der Nutzer soll auch hier zwischen GitHub und Google wählen können). `AccountsEvent.OpenUrl` entfällt dort.
- `AccountsScreen`: `onAddAccount` → `navController.navigate(Routes.LOGIN)`.

### 7. Settings anpassen
- Die Karte „GitHub OAuth" in `SettingsScreen` bleibt (Override für Client-ID/Secret) und wird als „Erweiterte Konfiguration" beschriftet, mit dem Hinweis, dass die ausgelieferte App bereits konfiguriert ist.
- Prüfen, ob `Routes.SETTINGS` ohne Konto erreichbar sein soll — mit dem Gate ist die Bottom-Leiste ohne Konto ausgeblendet, die Karte ist also nur nach dem Login erreichbar. Das ist in Ordnung, weil die Konfiguration auch auf dem Login-Screen möglich ist.

### 8. CI/Build: OAuth-App bereitstellen
- **Voraussetzung (Nutzeraktion, einmalig):** OAuth-App unter `https://github.com/settings/developers` → *New OAuth App* anlegen:
  - Application name: `Codespace Manager`
  - Homepage URL: Repository-URL
  - **Authorization callback URL:** `com.codespacemanager.app://oauth/callback` (exakt; Wildcard ist bei neu angelegten Apps aus)
- Client-ID und Client-Secret als Repository-Secrets **`GITHUB_CLIENT_ID`** und **`GITHUB_CLIENT_SECRET`** hinterlegen. Die Workflow-Referenz existiert bereits (`.github/workflows/android.yml`, `secretValue(...)` in `app/build.gradle.kts`); ob die Secrets tatsächlich gesetzt sind, konnte ich nicht auslesen (Token-Permission 403) → **vom Nutzer prüfen lassen**.
- Prüfen, ob GitHub die Custom-Schemen-Callback-URL akzeptiert. Falls nicht: auf **App Link** (`https://…` + `assetlinks.json`) umstellen — das ist die von RFC 8252 für Android empfohlene Variante und schützt gegen Schema-Hijacking durch andere Apps; erfordert eine gehostete Domain.
- `secretValue()`: prüfen, ob der Fallback für lokale Builds ohne Secrets sauber auf ""/null endet (kein Build-Bruch).

### 9. Tests & Doku
- `OAuthTest` (neu): `GITHUB` enthält `prompt=select_account`, `GOOGLE` enthält **kein** `prompt`; beide enthalten `code_challenge`/`state`/`scope`.
- `OAuthSessionTest`: Roundtrip über `SettingsStore` (Persistenz) — wenn ein Test-DataStore-Fixture existiert, dieses nutzen.
- Bestehende Tests, die `authorizeUrl`/`SetupViewModel`/`hasCredentials` referenzieren, nachziehen.
- `README.md`: neuer Abschnitt „Anmeldung" (Ablauf, Delegation an GitHub, Google-Hinweis, Hinweis auf Secret im APK).
- `docs/setup.md`: Setup-Schritt auf „OAuth-App mitgeliefert" umstellen, Callback-URL dokumentieren.
- `docs/api-reference.md`: Ablauf um `prompt`-Variante ergänzen.
- `docs/architecture.md`: Gate-Logik und neue Dateien nachziehen.

## Risiken und Gegenmaßnahmen

| Risiko | Gegenmaßnahme |
|---|---|
| Client-Secret liegt im APK und ist extrahierbar | GitHub verlangt es beim Web-Flow; PKCE verhindert, dass der Code ohne den Verifier tauschbar ist. Scopes minimal halten (`repo codespace read:org`), Callback-URL exakt binden, Auth-Token in `TokenStore` verschlüsselt (existiert bereits). **Kein** Client-Secret-Log, kein Secret in `git`. |
| „Continue with Google" erscheint auf GitHubs Seite nicht (z. B. abgeschaltet für Enterprise-Instanzen) | Der Button öffnet weiterhin die volle Anmeldeseite; der Nutzer nutzt dort Passwort/Passkey. **Auf dem Gerät verifizieren** (Schritt V4) und im README benennen. |
| Prozess wird im Browser-Rundgang beendet → „Kein laufender Login gefunden" | Aufgabe 5: Pending-OAuth in den DataStore persistieren. |
| „Continue with Google" ist bei bereits angemeldetem Browser nicht sichtbar (dann erscheint direkt die GitHub-Freigabeseite) | Dokumentiertes Verhalten (Entscheidung 4). Die Meldung auf dem Login-Screen weist darauf hin, dass GitHub im Browser geöffnet wird. |
| Build ohne Secrets (z. B. `pull_request` aus einem Fork) → keine Client-ID | Der Login-Screen klappt den Abschnitt „OAuth-App konfigurieren" **ausgeklappt** auf; die App bleibt benutzbar. |
| Nutzer erwartet ein natives Google-Fenster in der App | README und Screen-Hinweistext sagen explizit, dass GitHub im Browser geöffnet wird und dort die Google-Option liegt. Die Naht (`SignInProvider`) erlaubt eine spätere native Variante. |

## Validierung

- **V1 — CI:** Push nach `main` → Workflow „Build debug APK" muss grün sein (Unit-Tests, `lintDebug`, `assembleDebug`). Bestehende Test-Logging-Konfiguration (`testLogging` mit vollem Stacktrace) aus dem letzten Fix greift.
- **V2 — Unit-Tests:** `OAuthTest` (prompt-Variante) und der Persistenz-Roundtrip sind grün; keine Regression in den 66 bestehenden Tests.
- **V3 — Erste Bildschirm:** App starten ohne Konto → LoginScreen mit beiden Buttons, kein SetupScreen. Nach erfolgreichem Login → Codespaces-Screen.
- **V4 — Gerätetest (manuell, zwingend):** „Mit Google anmelden" öffnet GitHubs Anmeldeseite und **„Continue with Google"** ist sichtbar und führt zum Konto; danach zurück in die App, Token-Tausch erfolgreich. Zusätzlich: „Mit GitHub anmelden" zeigt den Kontoauswahl-Dialog; Abbrechen in GitHub → Fehlerkarte in der App, kein Absturz.
- **V5 — Fallback:** Build ohne Client-ID (z. B. `GITHUB_CLIENT_ID=""`) → LoginScreen zeigt den aufgeklappten Konfigurationsabschnitt; nach dem Speichern funktioniert der Login.
- **V6 — Multi-Account:** „Konten" → „Konto hinzufügen" öffnet die Login-Route (nicht direkt den Browser); Entfernen des letzten Kontos führt zurück zum LoginScreen.

## Bewusst nicht enthalten (Nachfolge)

- **Natives Google-Sign-in** (Credential Manager, Google-Cloud-Projekt, SHA-1-Fingerabdruck) — Entscheidung 2; die Naht `SignInProvider` ist vorbereitet.
- **Ablaufende Tokens** (`offline_access`, 8 h Access + 6 Monate Refresh) — eigenständige Aufgabe, betrifft `TokenStore`/`AccountRepository`.
- **App-Link statt Custom Scheme** (`https://` + `assetlinks.json`) — sinnvoll als Härtung, benötigt eine gehostete Domain.
- **Token-Introspection / Token-Revoke-UI** und eine „Konto von GitHub trennen"-Funktion.
