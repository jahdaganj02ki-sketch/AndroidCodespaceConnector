package com.codespacemanager.app.data.remote.editor

import com.codespacemanager.app.domain.gateway.EditorHealthGateway
import com.codespacemanager.app.domain.gateway.EditorProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Prueft, ob der code-server im WebView erreichbar ist. */
@Singleton
class EditorHealthClient @Inject constructor(
    private val httpClient: OkHttpClient,
) : EditorHealthGateway {

    override suspend fun probe(url: String): EditorProbeResult {
        val request = Request.Builder()
            .url(url)
            .header("X-Tunnel-Skip-AntiPhishing-Page", "true")
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        return try {
            val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
            val code = response.code
            response.close()
            when {
                code in 200..299 -> EditorProbeResult.OK
                code == 502 || code == 503 || code == 504 -> EditorProbeResult.STARTING
                else -> EditorProbeResult.UNREACHABLE
            }
        } catch (e: IOException) {
            EditorProbeResult.UNREACHABLE
        }
    }

    companion object {
        fun probeClient(base: OkHttpClient): OkHttpClient = base.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
