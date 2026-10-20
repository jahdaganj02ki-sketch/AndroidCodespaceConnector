package com.codespacemanager.app.domain.gateway

/** Information zu einer Erweiterung aus dem Open-VSX-Register. */
data class ExtensionInfo(
    val id: String,
    val version: String,
    val vsixUrl: String?,
)

interface OpenVsxGateway {
    suspend fun resolve(extensionId: String): ExtensionInfo?
}
