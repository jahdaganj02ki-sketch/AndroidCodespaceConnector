package com.codespacemanager.app.platform.customtabs

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb
import com.codespacemanager.app.ui.theme.AppPalette

/** Oeffnet URLs im Chrome Custom Tab (mit der GitHub-Farbe als Toolbar). */
object CustomTabsHelper {

    fun open(context: Context, url: String, toolbarColor: Int = AppPalette.SurfaceInset.toArgb()) {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
        val intent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(toolbarColor)
                    .build(),
            )
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .build()
        runCatching { intent.launchUrl(context, uri) }
            .onFailure {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
    }
}
