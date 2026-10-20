package com.codespacemanager.app.domain.usecase

/**
 * Bau der Browse-URL eines Dev-Tunnel-Ports (Konstruktion des `gh`-CLI):
 * `https://<codespace-name>-<port>.app.github.dev`
 */
object CodespaceUrls {

    const val BROWSE_DOMAIN = "app.github.dev"

    fun browseUrl(codespaceName: String, port: Int): String =
        "https://$codespaceName-$port.$BROWSE_DOMAIN"

    fun browseUrlWithFolder(codespaceName: String, port: Int, folder: String?): String {
        val base = browseUrl(codespaceName, port)
        val normalized = folder?.trim()?.trim('/').orEmpty()
        if (normalized.isEmpty()) return "$base/"
        return "$base/?folder=$normalized"
    }

    /** Web-UI des Codespaces in GitHub. */
    fun codespaceWebUi(codespaceName: String): String = "https://github.com/codespaces/$codespaceName"

    /** Host, der fuer die WebView-Allowlist zulaessig ist. */
    fun isAllowedEditorHost(host: String?, codespaceName: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val normalized = host.lowercase()
        return normalized.endsWith(".$BROWSE_DOMAIN") ||
            normalized.endsWith(".githubpreview.dev") ||
            normalized.endsWith(".devtunnels.ms")
    }
}
