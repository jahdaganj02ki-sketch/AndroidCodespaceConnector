package com.codespacemanager.app.data.remote.tunnel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TunnelAccessControlEntryDto(
    val type: String,
    val subjects: List<String> = emptyList(),
    val scopes: List<String> = emptyList(),
    val provider: String? = null,
    val isDeny: Boolean? = null,
)

@Serializable
data class TunnelAccessControlDto(
    val entries: List<TunnelAccessControlEntryDto> = emptyList(),
)

@Serializable
data class TunnelPortRequestDto(
    val portNumber: Int,
    val protocol: String = "http",
    val labels: List<String> = listOf("UserForwardedPort"),
    val accessControl: TunnelAccessControlDto,
)

@Serializable
data class TunnelPortResponseDto(
    val portNumber: Int = 0,
    val protocol: String = "http",
    val labels: List<String> = emptyList(),
    val accessControl: TunnelAccessControlDto? = null,
    @SerialName("portUri") val portUri: String? = null,
)

/**
 * Access-Control-Entries der Dev-Tunnel-API.
 *
 *  * oeffentlich  = `{ "type": "anonymous", "subjects": [], "scopes": ["connect"] }`
 *  * privat      = deny-Organization-ACE (Default des `gh`-CLI)
 */
object AccessControlEntries {

    const val TYPE_ANONYMOUS = "anonymous"
    const val TYPE_ORGANIZATION = "organization"
    const val SCOPE_CONNECT = "connect"

    fun anonymous(): TunnelAccessControlEntryDto = TunnelAccessControlEntryDto(
        type = TYPE_ANONYMOUS,
        subjects = emptyList(),
        scopes = listOf(SCOPE_CONNECT),
    )

    fun denyOrganization(): TunnelAccessControlEntryDto = TunnelAccessControlEntryDto(
        type = TYPE_ORGANIZATION,
        subjects = listOf("1"),
        scopes = listOf(SCOPE_CONNECT),
        provider = "github",
        isDeny = true,
    )
}
