package com.codespacemanager.app.data.repository

import com.codespacemanager.app.core.ApiException
import com.codespacemanager.app.data.remote.tunnel.AccessControlEntries
import com.codespacemanager.app.data.remote.tunnel.DevTunnelClient
import com.codespacemanager.app.domain.gateway.PortGateway
import com.codespacemanager.app.domain.gateway.PortSetupResult
import com.codespacemanager.app.domain.gateway.PortVisibility
import com.codespacemanager.app.domain.gateway.TunnelPort
import com.codespacemanager.app.domain.gateway.TunnelProperties
import javax.inject.Inject
import javax.inject.Singleton

/** Implementierung des [PortGateway] auf Basis der Dev-Tunnels-REST-API. */
@Singleton
class TunnelGatewayImpl @Inject constructor(
    private val devTunnelClient: DevTunnelClient,
) : PortGateway {

    override suspend fun getPort(properties: TunnelProperties, port: Int): TunnelPort? {
        val dto = devTunnelClient.getPort(properties, port) ?: return null
        val entries = dto.accessControl?.entries.orEmpty()
        val anonymous = entries.any {
            it.type.equals(AccessControlEntries.TYPE_ANONYMOUS, ignoreCase = true) &&
                it.scopes.any { scope -> scope.equals(AccessControlEntries.SCOPE_CONNECT, ignoreCase = true) } &&
                it.isDeny != true
        }
        val deny = entries.any { it.isDeny == true }
        return TunnelPort(
            portNumber = dto.portNumber.takeIf { it > 0 } ?: port,
            protocol = dto.protocol,
            labels = dto.labels,
            anonymousAccess = anonymous,
            organizationDeny = deny,
        )
    }

    override suspend fun setPort(
        properties: TunnelProperties,
        port: Int,
        visibility: PortVisibility,
    ): PortSetupResult {
        val entry = when (visibility) {
            PortVisibility.PUBLIC -> AccessControlEntries.anonymous()
            PortVisibility.PRIVATE -> AccessControlEntries.denyOrganization()
        }
        return try {
            val ok = devTunnelClient.putPort(properties, port, entry)
            PortSetupResult(success = ok)
        } catch (e: Exception) {
            PortSetupResult(success = false, errorMessage = e.message)
        }
    }

    override suspend fun isPublic(properties: TunnelProperties, port: Int): Boolean =
        getPort(properties, port)?.anonymousAccess ?: false
}
