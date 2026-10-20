package com.codespacemanager.app.platform.webview

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.http.SslError
import android.view.KeyEvent
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.codespacemanager.app.domain.usecase.CodespaceUrls
import okhttp3.OkHttpClient
import okhttp3.Request

/** Host-Allowlist fuer den eingebetteten Editor. */
object EditorHostPolicy {

    fun isAllowed(host: String?, codespaceName: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val normalized = host.lowercase()
        if (codespaceName != null) {
            val prefix = codespaceName.lowercase()
            if (normalized == "$prefix-13337.${CodespaceUrls.BROWSE_DOMAIN}") return true
        }
        return normalized.endsWith(".${CodespaceUrls.BROWSE_DOMAIN}") ||
            normalized.endsWith(".githubpreview.dev") ||
            normalized.endsWith(".devtunnels.ms")
    }
}

/**
 * WebView fuer den code-server-Editor.
 *
 *  * Host-Allowlist: nur Codespace-Hosts, alles andere geht in den Browser.
 *  * `X-Tunnel-Skip-AntiPhishing-Page` wird fuer das Hauptdokument injiziert.
 *  * SSL-Fehler werden nur fuer den bekannten Host akzeptiert.
 */
@SuppressLint("SetJavaScriptEnabled")
class CodeServerWebViewFactory(
    private val httpClient: OkHttpClient,
    private val codespaceName: String,
    private val connectAccessToken: String? = null,
    private val onExternalUrl: (String) -> Unit,
    private val onError: () -> Unit,
    private val onPageLoaded: () -> Unit,
) {

    fun create(context: android.content.Context): WebView = WebView(context).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(false)
            textZoom = 100
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        setBackgroundColor(Color.parseColor("#0D1117"))
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = true
        overScrollMode = WebView.OVER_SCROLL_NEVER

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        webViewClient = EditorWebViewClient()
    }

    private inner class EditorWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val url = request.url.toString()
            val host = request.url.host
            if (EditorHostPolicy.isAllowed(host, codespaceName)) return false
            onExternalUrl(url)
            return true
        }

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            val host = runCatching { android.net.Uri.parse(url).host }.getOrNull()
            if (EditorHostPolicy.isAllowed(host, codespaceName)) return false
            onExternalUrl(url)
            return true
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            if (!request.isForMainFrame) return null
            val host = request.url.host ?: return null
            if (!EditorHostPolicy.isAllowed(host, codespaceName)) return null
            return fetchWithTunnelHeaders(request)
        }

        override fun onPageFinished(view: WebView, url: String) {
            onPageLoaded()
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) onError()
        }

        @Suppress("DEPRECATION")
        override fun onReceivedError(
            view: WebView,
            errorCode: Int,
            description: String,
            failingUrl: String,
        ) {
            onError()
        }

        override fun onReceivedSslError(
            view: WebView,
            handler: SslErrorHandler,
            error: SslError,
        ) {
            val host = error.url?.let { runCatching { android.net.Uri.parse(it).host }.getOrNull() }
            if (EditorHostPolicy.isAllowed(host, codespaceName)) {
                handler.proceed()
            } else {
                handler.cancel()
            }
        }
    }

    private fun fetchWithTunnelHeaders(request: WebResourceRequest): WebResourceResponse? {
        return try {
            val builder = Request.Builder().url(request.url.toString())
            request.requestHeaders.forEach { (key, value) -> builder.header(key, value) }
            builder.header(HEADER_SKIP_ANTI_PHISHING, "true")
            if (!connectAccessToken.isNullOrBlank()) {
                builder.header(HEADER_TUNNEL_AUTHORIZATION, "tunnel $connectAccessToken")
            }
            val response = httpClient.newCall(builder.build()).execute()
            val body = response.body
            val headers = response.headers.toMultimap()
                .filterKeys { !it.equals("Content-Encoding", ignoreCase = true) }
                .mapValues { it.value.joinToString(", ") }
            WebResourceResponse(
                response.header("Content-Type", "text/html")?.substringBefore(';') ?: "text/html",
                null,
                response.code,
                response.message,
                headers,
                body?.byteStream(),
            )
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val HEADER_SKIP_ANTI_PHISHING = "X-Tunnel-Skip-AntiPhishing-Page"
        const val HEADER_TUNNEL_AUTHORIZATION = "X-Tunnel-Authorization"

        /** Tastatur im WebView ein-/ausblenden. */
        fun toggleKeyboard(webView: WebView) {
            webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE))
        }
    }
}

/** Eingebetteter code-server. */
@Composable
fun CodeServerWebView(
    url: String,
    codespaceName: String,
    httpClient: OkHttpClient,
    connectAccessToken: String? = null,
    onExternalUrl: (String) -> Unit,
    onError: () -> Unit,
    onPageLoaded: () -> Unit,
    modifier: Modifier = Modifier,
    webViewHolder: MutableWebViewHolder? = null,
) {
    val factory = CodeServerWebViewFactory(
        httpClient = httpClient,
        codespaceName = codespaceName,
        connectAccessToken = connectAccessToken,
        onExternalUrl = onExternalUrl,
        onError = onError,
        onPageLoaded = onPageLoaded,
    )
    AndroidView(
        modifier = modifier,
        factory = { context ->
            factory.create(context).also { view ->
                webViewHolder?.view = view
                view.loadUrl(url, mapOf(CodeServerWebViewFactory.HEADER_SKIP_ANTI_PHISHING to "true"))
            }
        },
        update = { view -> webViewHolder?.view = view },
    )
    DisposableEffect(Unit) {
        onDispose { webViewHolder?.view?.destroy() }
    }
}

/** Haelt die WebView-Instanz, damit Topbar-Aktionen sie erreichen. */
class MutableWebViewHolder {
    var view: WebView? = null
}
