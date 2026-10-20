package com.codespacemanager.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Prueft das Setup-Skript, das in jedes Repository geschrieben wird.
 * Die Datei liegt in den Assets der App; im Unit-Test wird sie aus dem Quellverzeichnis gelesen.
 */
class EnsureScriptTest {

    private val script: String by lazy {
        val file = File("src/main/assets/devcontainer/setup-code-server.sh")
        check(file.exists()) { "Setup-Skript nicht gefunden: ${file.absolutePath}" }
        file.readText()
    }

    @Test
    fun `skript installiert die erweiterungsliste ausserhalb des workspace`() {
        assertThat(script).contains("/usr/local/share/codespace-manager/extensions.txt")
        assertThat(script).contains("chmod 0644")
    }

    @Test
    fun `ensure skript liest ausschliesslich die root-owned liste`() {
        assertThat(script).contains("/usr/local/bin/ensure-extensions.sh")
        assertThat(script).contains("LIST=\"\$DIR/extensions.txt\"")
    }

    @Test
    fun `ensure skript nutzt gallery und vsix fallback ohne abbruch`() {
        assertThat(script).contains("code-server --install-extension")
        assertThat(script).contains("install_from_lock")
        assertThat(script).contains("|| echo \"[ensure-extensions] WARN")
    }

    @Test
    fun `entrypoint wird gewrappt und ensure laeuft vor dem code-server start`() {
        assertThat(script).contains("/usr/local/bin/code-server-entrypoint.real")
        assertThat(script).contains("exec /usr/local/bin/code-server-entrypoint.real")
        val ensureIndex = script.indexOf("/usr/local/bin/ensure-extensions.sh ||")
        val execIndex = script.indexOf("exec /usr/local/bin/code-server-entrypoint.real")
        assertThat(ensureIndex).isGreaterThan(0)
        assertThat(execIndex).isGreaterThan(ensureIndex)
    }

    @Test
    fun `nutzer wechsel wird beibehalten`() {
        assertThat(script).contains("\$_REMOTE_USER")
        assertThat(script).contains("exec su \"\$_REMOTE_USER\"")
    }

    @Test
    fun `theme und trusted origins werden konfiguriert`() {
        assertThat(script).contains("GitHub Dark Default")
        assertThat(script).contains("trusted-origins")
        assertThat(script).contains("@@EDITOR_PORT@@")
    }

    @Test
    fun `platzhalter werden ersetzt`() {
        val rendered = script
            .replace("@@EXTENSION_LIST@@", "kilocode.kilo-code\nsaoudrizwan.claude-dev")
            .replace("@@EDITOR_PORT@@", "13337")
        assertThat(rendered).doesNotContain("@@")
        assertThat(rendered).contains("kilocode.kilo-code")
        assertThat(rendered).contains("0.0.0.0:13337")
    }
}
