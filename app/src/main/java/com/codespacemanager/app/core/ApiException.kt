package com.codespacemanager.app.core

/** Fehler eines GitHub-API-Aufrufs mit HTTP-Status. */
class ApiException(
    val statusCode: Int,
    override val message: String,
    val allowPermissionsUrl: String? = null,
    val body: String? = null,
) : Exception(message) {

    val isUnauthorized: Boolean get() = statusCode == 401
    val isForbidden: Boolean get() = statusCode == 403
    val isUnprocessable: Boolean get() = statusCode == 422
    val isNotFound: Boolean get() = statusCode == 404
    val isConflict: Boolean get() = statusCode == 409
}

/** Fehler der Dev-Tunnel-API. */
class TunnelException(
    val statusCode: Int,
    override val message: String,
    val body: String? = null,
) : Exception(message)

/** Netzwerkfehler ohne HTTP-Status. */
class NetworkException(
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
