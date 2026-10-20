package com.codespacemanager.app.data.remote.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LinkHeaderTest {

    @Test
    fun `naechste seite wird aus dem link header gelesen`() {
        val header = "<https://api.github.com/user/repos?page=2>; rel=\"next\", " +
            "<https://api.github.com/user/repos?page=9>; rel=\"last\""
        assertThat(LinkHeader.nextPage(header)).isEqualTo(2)
    }

    @Test
    fun `ohne next relation gibt es keine naechste seite`() {
        val header = "<https://api.github.com/user/repos?page=1>; rel=\"prev\", " +
            "<https://api.github.com/user/repos?page=9>; rel=\"last\""
        assertThat(LinkHeader.nextPage(header)).isNull()
    }

    @Test
    fun `leerer header liefert null`() {
        assertThat(LinkHeader.nextPage(null)).isNull()
        assertThat(LinkHeader.nextPage("")).isNull()
    }
}
