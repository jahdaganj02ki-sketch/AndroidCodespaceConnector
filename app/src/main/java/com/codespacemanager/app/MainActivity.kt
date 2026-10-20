package com.codespacemanager.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.codespacemanager.app.ui.MainViewModel
import com.codespacemanager.app.ui.navigation.AppRoot
import com.codespacemanager.app.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppRoot(viewModel = viewModel)
            }
        }
        handleCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallback(intent)
    }

    private fun handleCallback(intent: Intent?) {
        val data = intent?.data ?: return
        if (!data.scheme.equals(OAuthScheme, ignoreCase = true)) return
        viewModel.handleCallback(data.toString())
    }

    private companion object {
        const val OAuthScheme = "com.codespacemanager.app"
    }
}
