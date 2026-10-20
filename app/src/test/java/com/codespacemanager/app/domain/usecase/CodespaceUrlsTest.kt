package com.codespacemanager.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CodespaceUrlsTest {

    @Test
    fun `browse url entspricht der gh-cli konstruktion`() {
        assertThat(CodespaceUrls.browseUrl("glowing-spoon-1234", 13337))
            .isEqualTo("https://glowing-spoon-1234-13337.app.github.dev")
    }

    @Test
    fun `browse url mit ordner haengt den ordner an`() {
        assertThat(CodespaceUrls.browseUrlWithFolder("abc-1", 13337, "/workspaces/demo"))
            .isEqualTo("https://abc-1-13337.app.github.dev/?folder=workspaces/demo")
    }

    @Test
    fun `browse url ohne ordner endet mit slash`() {
        assertThat(CodespaceUrls.browseUrlWithFolder("abc-1", 13337, null))
            .isEqualTo("https://abc-1-13337.app.github.dev/")
    }

    @Test
    fun `codespace web ui zeigt auf github`() {
        assertThat(CodespaceUrls.codespaceWebUi("abc-1"))
            .isEqualTo("https://github.com/codespaces/abc-1")
    }

    @Test
    fun `nur codespace hosts sind erlaubt`() {
        assertThat(CodespaceUrls.isAllowedEditorHost("abc-1-13337.app.github.dev", "abc-1")).isTrue()
        assertThat(CodespaceUrls.isAllowedEditorHost("x.githubpreview.dev", "abc-1")).isTrue()
        assertThat(CodespaceUrls.isAllowedEditorHost("x.devtunnels.ms", "abc-1")).isTrue()
        assertThat(CodespaceUrls.isAllowedEditorHost("github.com", "abc-1")).isFalse()
        assertThat(CodespaceUrls.isAllowedEditorHost("evil.example.com", "abc-1")).isFalse()
        assertThat(CodespaceUrls.isAllowedEditorHost(null, "abc-1")).isFalse()
    }
}
