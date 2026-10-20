package com.codespacemanager.app.data.remote.tunnel

import com.codespacemanager.app.domain.gateway.TunnelProperties
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test

class DevTunnelClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: DevTunnelClient
    private lateinit var settings: FakeSettingsGateway

    private val properties = TunnelProperties(
        serviceUri = "https://use.rel.tunnels.api.visualstudio.com/",
        tunnelId = "tunnel-123",
        connectAccessToken = "connect-token",
        managePortsAccessToken = "manage-token",
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        settings = FakeSettingsGateway()
        client = DevTunnelClient(OkHttpClient(), settings)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun localProperties(): TunnelProperties = properties.copy(
        serviceUri = server.url("/").toString(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `oeffentlicher port sendet anonymes access control entry`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val ok = client.putPort(localProperties(), 13337, AccessControlEntries.anonymous())

        assertThat(ok).isTrue()
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).contains("/tunnels/tunnel-123/ports/13337")
        assertThat(request.path).contains("api-version=2023-09-27-preview")
        assertThat(request.getHeader("Authorization")).isEqualTo("Tunnel manage-token")
        assertThat(request.getHeader("If-None-Match")).isEqualTo("*")
        assertThat(request.getHeader("Content-Type")).contains("application/json")

        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        val entries = body.getValue("accessControl").jsonObject.getValue("entries")
        val entry = entries.jsonArray.first().jsonObject
        assertThat(entry.getValue("type").jsonPrimitive.content).isEqualTo("anonymous")
        assertThat(entry.getValue("scopes").toString()).contains("connect")
        assertThat(body.getValue("portNumber").jsonPrimitive.content).isEqualTo("13337")
        assertThat(body.getValue("protocol").jsonPrimitive.content).isEqualTo("http")
    }

    @Test
    fun `privater port sendet deny organization entry`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        client.putPort(localProperties(), 13337, AccessControlEntries.denyOrganization())

        val request = server.takeRequest()
        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        val entry = body.getValue("accessControl").jsonObject.getValue("entries")
            .jsonArray.first().jsonObject
        assertThat(entry.getValue("type").jsonPrimitive.content).isEqualTo("organization")
        assertThat(entry.getValue("provider").jsonPrimitive.content).isEqualTo("github")
        assertThat(entry.getValue("isDeny").jsonPrimitive.content).isEqualTo("true")
    }

    @Test
    fun `bei 412 wird mit if-match aktualisiert`() = runTest {
        server.enqueue(MockResponse().setResponseCode(412))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val ok = client.putPort(localProperties(), 13337, AccessControlEntries.anonymous())

        assertThat(ok).isTrue()
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertThat(first.getHeader("If-None-Match")).isEqualTo("*")
        assertThat(second.getHeader("If-Match")).isEqualTo("*")
    }

    @Test
    fun `falsche pfadvariante wird erkannt und die funktionierende gemerkt`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val ok = client.putPort(localProperties(), 13337, AccessControlEntries.anonymous())

        assertThat(ok).isTrue()
        val first = server.takeRequest() as RecordedRequest
        val second = server.takeRequest() as RecordedRequest
        assertThat(first.path).contains("/tunnels/tunnel-123/ports/13337")
        assertThat(second.path).contains("/api/v1/tunnels/tunnel-123/ports/13337")
        assertThat(settings.current().tunnelApiPathVariant).isEqualTo("/api/v1/tunnels")
    }

    @Test
    fun `gemerkte pfadvariante wird zuerst verwendet`() = runTest {
        settings.update { it.copy(tunnelApiPathVariant = "/api/v1/tunnels") }
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        client.putPort(localProperties(), 13337, AccessControlEntries.anonymous())

        val request = server.takeRequest()
        assertThat(request.path).contains("/api/v1/tunnels/tunnel-123/ports/13337")
    }

    @Test
    fun `port lesen liefert die anonymen access control informationen`() = runTest {
        val body = """
            {
              "portNumber": 13337,
              "protocol": "http",
              "labels": ["UserForwardedPort"],
              "accessControl": {
                "entries": [
                  { "type": "anonymous", "subjects": [], "scopes": ["connect"] }
                ]
              }
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val port = client.getPort(localProperties(), 13337)

        assertThat(port).isNotNull()
        assertThat(port!!.portNumber).isEqualTo(13337)
        val entry = port.accessControl!!.entries.first()
        assertThat(entry.type).isEqualTo("anonymous")
        assertThat(entry.scopes).containsExactly("connect")
    }
}
