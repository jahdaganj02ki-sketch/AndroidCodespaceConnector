package com.codespacemanager.app.domain.usecase

/**
 * `devcontainer.json` darf Kommentare enthalten (JSONC). Fuer die Verarbeitung in der App
 * werden Kommentare und Trailing Commas entfernt; die Datei wird anschliessend als
 * regulaeres JSON neu geschrieben (der Originalzustand bleibt im Default-Branch erhalten).
 */
object Jsonc {

    /** Entfernt Zeilen- und Blockkommentare ausserhalb von Strings. */
    fun stripComments(input: String): String {
        val out = StringBuilder(input.length)
        var i = 0
        var inString = false
        var escaped = false
        while (i < input.length) {
            val c = input[i]
            if (inString) {
                out.append(c)
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                i++
                continue
            }
            when {
                c == '"' -> {
                    inString = true
                    out.append(c)
                    i++
                }

                c == '/' && i + 1 < input.length && input[i + 1] == '/' -> {
                    while (i < input.length && input[i] != '\n') i++
                }

                c == '/' && i + 1 < input.length && input[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < input.length && !(input[i] == '*' && input[i + 1] == '/')) i++
                    i = minOf(i + 2, input.length)
                }

                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    /** Entfernt Kommas vor `}` bzw. `]`. */
    fun stripTrailingCommas(input: String): String {
        val out = StringBuilder(input.length)
        var inString = false
        var escaped = false
        for (i in input.indices) {
            val c = input[i]
            if (inString) {
                out.append(c)
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            if (c == '"') {
                inString = true
                out.append(c)
                continue
            }
            if (c == ',') {
                var j = i + 1
                while (j < input.length && input[j].isWhitespace()) j++
                if (j < input.length && (input[j] == '}' || input[j] == ']')) {
                    continue
                }
            }
            out.append(c)
        }
        return out.toString()
    }

    /** Kommentare und Trailing Commas entfernen. */
    fun sanitize(input: String): String = stripTrailingCommas(stripComments(input))
}
