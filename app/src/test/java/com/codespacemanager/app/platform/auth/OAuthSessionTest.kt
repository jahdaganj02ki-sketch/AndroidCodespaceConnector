package com.codespacemanager.app.platform.auth

import com.codespacemanager.app.data.remote.tunnel.FakeSettingsGateway
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OAuthSessionTest {

    @Test
    fun `begin speichert den Flow in den Einstellungen`() = runTest {
        val gateway = FakeSettingsGateway()
        val session = OAuthSession(gateway)

        session.begin(OAuthRequest(state = "state-1", codeVerifier = "verifier-1"))

        val settings = gateway.current()
        assertThat(settings.pendingOAuthState).isEqualTo("state-1")
        assertThat(settings.pendingOAuthVerifier).isEqualTo("verifier-1")
    }

    @Test
    fun `consume ueberlebt einen neuen Prozess und loescht den Flow`() = runTest {
        val gateway = FakeSettingsGateway()
        OAuthSession(gateway).begin(OAuthRequest(state = "state-1", codeVerifier = "verifier-1"))

        val wiederhergestellt = OAuthSession(gateway).consume()

        assertThat(wiederhergestellt?.state).isEqualTo("state-1")
        assertThat(wiederhergestellt?.codeVerifier).isEqualTo("verifier-1")
        assertThat(gateway.current().pendingOAuthState).isEmpty()
        assertThat(gateway.current().pendingOAuthVerifier).isEmpty()
    }

    @Test
    fun `consume ohne laufenden Flow liefert null`() = runTest {
        val session = OAuthSession(FakeSettingsGateway())

        assertThat(session.consume()).isNull()
    }
}
