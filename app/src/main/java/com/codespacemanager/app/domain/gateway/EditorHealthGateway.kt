package com.codespacemanager.app.domain.gateway

/** Ergebnis einer Editor-Erreichbarkeitspruefung. */
enum class EditorProbeResult { OK, STARTING, UNREACHABLE }

/** Prueft, ob der code-server im WebView erreichbar ist. */
interface EditorHealthGateway {
    suspend fun probe(url: String): EditorProbeResult
}
