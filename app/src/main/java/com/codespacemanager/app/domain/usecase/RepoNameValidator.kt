package com.codespacemanager.app.domain.usecase

/**
 * Validierung eines Repository-Namens nach den GitHub-Regeln:
 * Buchstaben, Ziffern, `-`, `_` und `.`; 1–100 Zeichen; keine `.`/`..`.
 */
object RepoNameValidator {

    const val MAX_LENGTH = 100

    private val ALLOWED = Regex("^[A-Za-z0-9._-]+$")

    fun validate(name: String): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "Name darf nicht leer sein"
            trimmed.length > MAX_LENGTH -> "Name darf höchstens $MAX_LENGTH Zeichen haben"
            trimmed == "." || trimmed == ".." -> "Ungültiger Name"
            !ALLOWED.matches(trimmed) ->
                "Erlaubt sind Buchstaben, Ziffern sowie -, _ und ."

            else -> null
        }
    }

    fun isValid(name: String): Boolean = validate(name) == null
}
