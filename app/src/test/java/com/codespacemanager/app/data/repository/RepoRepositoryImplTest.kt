package com.codespacemanager.app.data.repository

import com.codespacemanager.app.data.remote.api.OrgRepoApi
import com.codespacemanager.app.data.remote.api.RepoApi
import com.codespacemanager.app.data.remote.api.UserApi
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import java.util.Base64

class RepoRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: RepoRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        repository = RepoRepositoryImpl(
            repoApi = retrofit.create(RepoApi::class.java),
            orgRepoApi = retrofit.create(OrgRepoApi::class.java),
            userApi = retrofit.create(UserApi::class.java),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `repos werden gemappt und die naechste seite aus dem link header gelesen`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(
                    "Link",
                    "<${server.url("/user/repos?page=2")}>; rel=\"next\", " +
                        "<${server.url("/user/repos?page=5")}>; rel=\"last\"",
                )
                .setBody(
                    """
                    [
                      {
                        "id": 7,
                        "name": "demo",
                        "full_name": "octo/demo",
                        "owner": { "login": "octo", "type": "User" },
                        "private": true,
                        "default_branch": "main"
                      }
                    ]
                    """.trimIndent(),
                ),
        )

        val page = repository.listUserRepos(1)

        assertThat(page.items).hasSize(1)
        assertThat(page.items.first().fullName).isEqualTo("octo/demo")
        assertThat(page.items.first().isPrivate).isTrue()
        assertThat(page.nextPage).isEqualTo(2)
    }

    @Test
    fun `dateiinhalt wird aus base64 dekodiert`() = runTest {
        val encoded = Base64.getEncoder().encodeToString("{\"name\":\"x\"}".toByteArray())
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "path": ".devcontainer/devcontainer.json", "sha": "abc", "content": "$encoded", "encoding": "base64" }""",
            ),
        )

        val content = repository.getFileContent("octo/demo", ".devcontainer/devcontainer.json", "main")

        assertThat(content).isNotNull()
        assertThat(content!!.content).isEqualTo("{\"name\":\"x\"}")
        assertThat(content.sha).isEqualTo("abc")
    }

    @Test
    fun `fehlende datei liefert null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))

        val content = repository.getFileContent("octo/demo", "missing.json", "main")

        assertThat(content).isNull()
    }

    @Test
    fun `datei wird mit branch und base64 geschrieben`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"sha":"newsha"}"""))

        repository.putFileContent(
            fullName = "octo/demo",
            path = ".devcontainer/devcontainer.json",
            content = "hello",
            message = "msg",
            branch = "codespace/editor",
            sha = null,
        )

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).contains(".devcontainer/devcontainer.json")
        val body = request.body.readUtf8()
        assertThat(body).contains("codespace/editor")
        assertThat(body).contains(Base64.getEncoder().encodeToString("hello".toByteArray()))
    }

    @Test
    fun `branch wird erstellt`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"ref":"refs/heads/codespace/editor","object":{"sha":"sha"}}"""))

        val created = repository.createBranch("octo/demo", "codespace/editor", "sha")

        assertThat(created).isTrue()
        val request = server.takeRequest()
        assertThat(request.path).endsWith("/git/refs")
        assertThat(request.body.readUtf8()).contains("refs/heads/codespace/editor")
    }

    @Test
    fun `fehlender branch liefert null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))

        assertThat(repository.getBranchSha("octo/demo", "codespace/editor")).isNull()
    }
}
