# Architektur

Kurzer Überblick über die Schichten, den Anmeldefluss und das Start-Gate der App.

## 1. Schichten

```
ui/         Compose-Screens + ViewModels (StateFlows, Events)
  navigation/  Routen, Bottom-Leiste, Start-Gate
  screens/     login, accounts, repos, repodetail, codespaces, start, editor, settings, help
domain/     Modelle, Ports (Gateway-Interfaces), Use-Cases   (ohne Android-Abhängigkeiten)
data/       Adapter: remote/{api,tunnel,openvsx,auth,editor,update}, local/{db,datastore,tokenstore,assets}, repository/
platform/   Android-nahe Bausteine: webview, customtabs, crypto, auth
di/         Hilt-Module (@Binds/@Provides, SingletonComponent)
```

Abhängigkeitsrichtung: `ui → domain ← data`, `platform` wird von `data` und `ui` genutzt,
`domain` kennt weder Android noch Hilt.

## 2. Anmeldefluss

| Schritt | Ort |
|---|---|
| Button „Mit GitHub/Google anmelden“ | `ui/screens/login/LoginScreen.kt` → `LoginViewModel.signIn(provider)` |
| Autorisierungs-URL bauen | `data/repository/AuthCoordinator.authorizeUrl(provider)` → `platform/auth/OAuth.authorizeUrl` |
| Flow starten (State + Verifier) | `platform/auth/OAuthSession.begin` → DataStore (`pending_oauth_state`, `pending_oauth_verifier`) |
| Browser öffnen | `platform/customtabs/CustomTabsHelper.open` |
| Redirect (`com.codespacemanager.app://oauth/callback`) | `MainActivity.onCreate/onNewIntent` → `MainViewModel.handleCallback` |
| Callback auswerten + Token tauschen | `AuthCoordinator.handleCallback` → `AccountRepository.addAccount` |
| Status anzeigen | `AuthCoordinator.status` (`domain/model/AuthStatus.kt`) → `LoginViewModel.state` |

* `SignInProvider.GITHUB` setzt `prompt=select_account` (Kontoauswahl), `SignInProvider.GOOGLE`
  lässt `prompt` weg – GitHub blendet dann seine volle Anmeldeseite inkl. „Continue with Google“
  ein. Beide Wege laufen über GitHubs Seite; die App enthält kein Google-SDK.
* Der laufende Flow liegt im **DataStore**, nicht nur im RAM: ein Prozessneustart während des
  Browser-Rundgangs bricht den Login nicht ab.
* `AuthCoordinator` ist ein `@Singleton` und hält den gemeinsamen `AuthStatus` – dadurch sehen
  Start-Gate und Route „Konto hinzufügen“ denselben Anmeldestatus.

## 3. Start-Gate

`ui/navigation/AppNavigation.kt` entscheidet anhand der Kontenliste:

| Zustand | Anzeige |
|---|---|
| `accounts == null` (noch nicht geladen) | `LoadingState` |
| `accounts` leer | `LoginScreen` ohne Scaffold/Bottom-Leiste |
| `accounts` vorhanden | `Scaffold` + `NavHost` (Start: Codespaces) |

Die Kontenliste kommt aus `MainViewModel.accounts` (Room-Flow, `null` = noch nicht geladen).
Wird das letzte Konto entfernt, ist die Liste wieder leer und die App zeigt erneut den
Login-Screen. Die Bottom-Leiste kennt die Route `login` nicht – sie ist dort ausgeblendet.

Der Login-Screen wird an zwei Stellen verwendet (Gate und Route `login`). Beide teilen sich
denselben ViewModel, indem er an den `ViewModelStoreOwner` der Activity gehängt wird
(`activityViewModelStoreOwner()` in `AppNavigation.kt`).

## 4. Persistenz

| Daten | Ort |
|---|---|
| Konten, Codespaces | Room (`data/local/db`) |
| Einstellungen, OAuth-Overrides, laufender OAuth-Flow | DataStore/Preferences (`data/local/datastore`) |
| Access-Tokens | `EncryptedSharedPreferences` + AES/GCM-Schlüssel aus dem Android Keystore |
| Client-ID/Secret aus dem Build | `BuildConfig` (Fallback, überschreibbar per DataStore) |
