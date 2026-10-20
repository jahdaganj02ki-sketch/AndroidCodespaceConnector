package com.codespacemanager.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.codespacemanager.app.ui.MainViewModel
import com.codespacemanager.app.ui.screens.accounts.AccountsScreen
import com.codespacemanager.app.ui.screens.codespaces.CodespacesScreen
import com.codespacemanager.app.ui.screens.editor.EditorScreen
import com.codespacemanager.app.ui.screens.help.HelpScreen
import com.codespacemanager.app.ui.screens.repodetail.RepoDetailScreen
import com.codespacemanager.app.ui.screens.repos.ReposScreen
import com.codespacemanager.app.ui.screens.settings.SettingsScreen
import com.codespacemanager.app.ui.screens.setup.SetupScreen
import com.codespacemanager.app.ui.screens.start.StartCodespaceScreen
import com.codespacemanager.app.ui.theme.AppTheme

object Routes {
    const val ACCOUNTS = "accounts"
    const val REPOS = "repos"
    const val CODESPACES = "codespaces"
    const val SETTINGS = "settings"
    const val SETUP = "setup"
    const val HELP = "help"
    const val REPO_DETAIL = "repo/{fullName}"
    const val START = "start/{fullName}"
    const val EDITOR = "editor/{codespaceName}"

    fun repoDetail(fullName: String): String = "repo/${Uri.encode(fullName)}"
    fun start(fullName: String): String = "start/${Uri.encode(fullName)}"
    fun editor(codespaceName: String): String = "editor/${Uri.encode(codespaceName)}"
}

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomItems = listOf(
    BottomItem(Routes.CODESPACES, "Codespaces", Icons.Outlined.Code),
    BottomItem(Routes.REPOS, "Repos", Icons.Outlined.FolderOpen),
    BottomItem(Routes.ACCOUNTS, "Konten", Icons.Outlined.AccountCircle),
    BottomItem(Routes.SETTINGS, "Einstellungen", Icons.Outlined.Settings),
)

@Composable
fun AppRoot(viewModel: MainViewModel) {
    val hasCredentials by viewModel.hasCredentials.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    if (!hasCredentials) {
        SetupScreen(
            onSaved = { /* Einstellungen sind gespeichert, UI baut sich neu auf. */ },
        )
        return
    }

    Scaffold(
        containerColor = AppTheme.colors.canvas,
        bottomBar = { AppBottomBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CODESPACES,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.CODESPACES) {
                CodespacesScreen(
                    onOpenRepo = { navController.navigate(Routes.repoDetail(it)) },
                    onOpenEditor = { navController.navigate(Routes.editor(it)) },
                )
            }
            composable(Routes.REPOS) {
                ReposScreen(
                    onOpenRepo = { navController.navigate(Routes.repoDetail(it)) },
                )
            }
            composable(Routes.ACCOUNTS) {
                AccountsScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenHelp = { navController.navigate(Routes.HELP) },
                )
            }
            composable(Routes.SETUP) {
                SetupScreen(onSaved = {})
            }
            composable(Routes.HELP) {
                HelpScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.REPO_DETAIL) { entry ->
                val fullName = entry.arguments?.getString("fullName").orEmpty()
                RepoDetailScreen(
                    repoFullName = fullName,
                    onStart = { navController.navigate(Routes.start(fullName)) },
                    onOpenEditor = { navController.navigate(Routes.editor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.START) { entry ->
                val fullName = entry.arguments?.getString("fullName").orEmpty()
                StartCodespaceScreen(
                    repoFullName = fullName,
                    onOpenEditor = { navController.navigate(Routes.editor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.EDITOR) { entry ->
                val name = entry.arguments?.getString("codespaceName").orEmpty()
                EditorScreen(
                    codespaceName = name,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun AppBottomBar(navController: NavHostController) {
    val colors = AppTheme.colors
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val visible = currentRoute in bottomItems.map { it.route }

    if (!visible) return

    NavigationBar(
        containerColor = colors.surfaceInset,
        contentColor = colors.textPrimary,
    ) {
        bottomItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = {
                    Text(item.label, style = MaterialTheme.typography.labelSmall)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.accent,
                    selectedTextColor = colors.accent,
                    unselectedIconColor = colors.textMuted,
                    unselectedTextColor = colors.textMuted,
                    indicatorColor = colors.accentSubtle,
                ),
            )
        }
    }
}
