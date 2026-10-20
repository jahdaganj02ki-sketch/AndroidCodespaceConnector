package com.codespacemanager.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RepoNameValidatorTest {

    @Test
    fun `gueltige namen werden akzeptiert`() {
        assertThat(RepoNameValidator.isValid("codespace-manager")).isTrue()
        assertThat(RepoNameValidator.isValid("my_repo")).isTrue()
        assertThat(RepoNameValidator.isValid("app.v2")).isTrue()
        assertThat(RepoNameValidator.isValid("a")).isTrue()
    }

    @Test
    fun `leerer name ist ungueltig`() {
        assertThat(RepoNameValidator.validate("")).isEqualTo("Name darf nicht leer sein")
        assertThat(RepoNameValidator.validate("   ")).isNotNull()
    }

    @Test
    fun `zu langer name ist ungueltig`() {
        val name = "a".repeat(101)
        assertThat(RepoNameValidator.isValid(name)).isFalse()
    }

    @Test
    fun `ungueltige zeichen werden abgelehnt`() {
        assertThat(RepoNameValidator.isValid("mit leerzeichen")).isFalse()
        assertThat(RepoNameValidator.isValid("emoji✨")).isFalse()
        assertThat(RepoNameValidator.isValid("slash/name")).isFalse()
    }

    @Test
    fun `punkt und doppelpunkt sind ungueltig`() {
        assertThat(RepoNameValidator.isValid(".")).isFalse()
        assertThat(RepoNameValidator.isValid("..")).isFalse()
    }
}
