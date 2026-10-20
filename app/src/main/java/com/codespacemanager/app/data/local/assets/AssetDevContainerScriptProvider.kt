package com.codespacemanager.app.data.local.assets

import android.content.Context
import com.codespacemanager.app.domain.usecase.DevContainerScriptProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Liefert die devcontainer-Skripte aus den Assets der App. */
@Singleton
class AssetDevContainerScriptProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : DevContainerScriptProvider {

    override suspend fun setupCodeServerScript(extensions: List<String>, editorPort: Int): String {
        val raw = readAsset("devcontainer/setup-code-server.sh")
        return raw
            .replace(EXTENSION_LIST_PLACEHOLDER, extensions.joinToString("\n"))
            .replace(EDITOR_PORT_PLACEHOLDER, editorPort.toString())
    }

    override suspend fun setupAndroidScript(): String = readAsset("devcontainer/setup-android.sh")

    override suspend fun extensionsText(extensions: List<String>): String =
        extensions.joinToString("\n", postfix = "\n")

    private fun readAsset(path: String): String = context.assets.open(path).bufferedReader().use { it.readText() }

    companion object {
        const val EXTENSION_LIST_PLACEHOLDER = "@@EXTENSION_LIST@@"
        const val EDITOR_PORT_PLACEHOLDER = "@@EDITOR_PORT@@"
    }
}
