package com.codespacemanager.app.domain.usecase

import com.codespacemanager.app.core.ApiException
import com.codespacemanager.app.data.remote.tunnel.FakeSettingsGateway
import com.codespacemanager.app.domain.gateway.PortVisibility
import com.codespacemanager.app.domain.model.Geo
import com.codespacemanager.app.domain.model.PermissionsCheck
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class StartCodespaceUseCaseTest {

    private fun useCase(
        repoGateway: FakeRepoGateway = FakeRepoGateway(),
        codespaceGateway: FakeCodespaceGateway = FakeCodespaceGateway(),
        portGateway: FakePortGateway = FakePortGateway(),
        editorHealth: FakeEditorHealth = FakeEditorHealth(),
    ): StartCodespaceUseCase = StartCodespaceUseCase(
        codespaceGateway = codespaceGateway,
        repoGateway = repoGateway,
        portGateway = portGateway,
        prepareRepoUseCase = PrepareRepoUseCase(
            repoGateway = repoGateway,
            scriptProvider = FakeScriptProvider(),
            settingsGateway = FakeSettingsGateway(),
        ),
        editorHealthGateway = editorHealth,
    )

    private fun params(
        repoGateway: FakeRepoGateway = FakeRepoGateway(),
        devcontainerPath: String? = null,
    ) = StartCodespaceParams(
        repo = repoGateway.repo,
        machine = "basicLinux32gb",
        geo = Geo.EUROPE_WEST,
        idleTimeoutMinutes = 30,
        devcontainerPath = devcontainerPath,
    )

    @Test
    fun `happy path endet im ready zustand`() = runTest {
        val repoGateway = FakeRepoGateway()
        val portGateway = FakePortGateway()
        val states = useCase(repoGateway, portGateway = portGateway)
            .start(params(repoGateway))
            .toList()

        assertThat(states.first()).isInstanceOf(StartCodespaceState.CheckingRepo::class.java)
        assertThat(states).contains(StartCodespaceState.CreatingCodespace("octo/demo"))
        assertThat(states).contains(StartCodespaceState.ConnectingPort("demo-1"))
        assertThat(states).contains(StartCodespaceState.PreparingExtensions("demo-1", true))
        val ready = states.last()
        assertThat(ready).isInstanceOf(StartCodespaceState.Ready::class.java)
        val readyState = ready as StartCodespaceState.Ready
        assertThat(readyState.browseUrl).isEqualTo("https://demo-1-13337.app.github.dev")
        assertThat(readyState.portPublic).isTrue()
        assertThat(portGateway.lastVisibility).isEqualTo(PortVisibility.PUBLIC)
    }

    @Test
    fun `fehlende codespace berechtigung fuehrt zum quota hinweis`() = runTest {
        val repoGateway = FakeRepoGateway(
            permissions = PermissionsCheck(canCreateCodespaces = false, reason = "no quota"),
        )
        val states = useCase(repoGateway).start(params(repoGateway)).toList()

        val failed = states.last()
        assertThat(failed).isInstanceOf(StartCodespaceState.Failed::class.java)
        assertThat((failed as StartCodespaceState.Failed).quotaHint).isTrue()
    }

    @Test
    fun `401 mit allow permissions url fuehrt zur freigabe url`() = runTest {
        val repoGateway = FakeRepoGateway()
        val codespaceGateway = FakeCodespaceGateway(
            createError = ApiException(
                statusCode = 401,
                message = "not authorized",
                allowPermissionsUrl = "https://github.com/octo/demo/settings/codespaces",
            ),
        )
        val states = useCase(repoGateway, codespaceGateway).start(params(repoGateway)).toList()

        val failed = states.last() as StartCodespaceState.Failed
        assertThat(failed.allowPermissionsUrl)
            .isEqualTo("https://github.com/octo/demo/settings/codespaces")
    }

    @Test
    fun `fehlgeschlagener port fuehrt zum browser fallback`() = runTest {
        val repoGateway = FakeRepoGateway()
        val portGateway = FakePortGateway(succeed = false)
        val states = useCase(repoGateway, portGateway = portGateway)
            .start(params(repoGateway))
            .toList()

        val failed = states.last() as StartCodespaceState.Failed
        assertThat(failed.browserFallbackUrl).isEqualTo("https://github.com/codespaces/demo-1")
    }

    @Test
    fun `bereits vorhandener codespace mit gleichem devcontainer wird wiederverwendet`() = runTest {
        val repoGateway = FakeRepoGateway()
        val codespaceGateway = FakeCodespaceGateway(
            codespaces = listOf(
                com.codespacemanager.app.domain.model.Codespace(
                    name = "existing-1",
                    repositoryFullName = "octo/demo",
                    state = com.codespacemanager.app.domain.model.CodespaceState.SHUTDOWN,
                    devcontainerPath = DevContainerTemplates.DEVCONTAINER_PATH,
                ),
            ),
        )
        val states = useCase(repoGateway, codespaceGateway)
            .start(params(repoGateway, DevContainerTemplates.DEVCONTAINER_PATH))
            .toList()

        assertThat(codespaceGateway.created).isFalse()
        assertThat(codespaceGateway.started).isEqualTo(1)
        assertThat(states).contains(StartCodespaceState.ConnectingPort("existing-1"))
    }

    @Test
    fun `codespace mit anderem devcontainer wird nicht wiederverwendet`() = runTest {
        val repoGateway = FakeRepoGateway()
        val codespaceGateway = FakeCodespaceGateway(
            codespaces = listOf(
                com.codespacemanager.app.domain.model.Codespace(
                    name = "web-1",
                    repositoryFullName = "octo/demo",
                    state = com.codespacemanager.app.domain.model.CodespaceState.AVAILABLE,
                    devcontainerPath = ".devcontainer/other.json",
                ),
            ),
        )
        useCase(repoGateway, codespaceGateway)
            .start(params(repoGateway, DevContainerTemplates.DEVCONTAINER_PATH))
            .toList()

        assertThat(codespaceGateway.created).isTrue()
    }

    @Test
    fun `editor der nicht antwortet fuehrt zum hinweis auf neuanlage`() = runTest {
        val repoGateway = FakeRepoGateway()
        val editorHealth = FakeEditorHealth(reachable = false)
        val states = useCase(repoGateway, editorHealth = editorHealth)
            .start(params(repoGateway))
            .toList()

        val failed = states.last() as StartCodespaceState.Failed
        assertThat(failed.offerNewCodespace).isTrue()
        assertThat(editorHealth.probes).isEqualTo(StartCodespaceUseCase.EDITOR_POLL_ATTEMPTS)
    }
}
