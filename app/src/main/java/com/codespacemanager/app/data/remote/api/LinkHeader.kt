package com.codespacemanager.app.data.remote.api

/**
 * Auswertung des `Link`-Headers von GitHub fuer die Paginierung.
 * Beispiel:
 * `<https://api.github.com/user/repos?page=2>; rel="next", <https://api.github.com/user/repos?page=5>; rel="last"`
 */
object LinkHeader {

    fun nextPage(link: String?): Int? {
        if (link.isNullOrBlank()) return null
        return link.split(",")
            .map { it.trim() }
            .firstOrNull { it.contains("rel=\"next\"") }
            ?.let { part ->
                Regex("page=(\\d+)").find(part)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
    }
}
