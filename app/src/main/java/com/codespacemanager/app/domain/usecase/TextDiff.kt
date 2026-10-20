package com.codespacemanager.app.domain.usecase

/**
 * Zeilenweiser Diff (LCS) fuer die Vorschau im Bestaetigungsdialog.
 * Reicht fuer devcontainer.json und Skript-Dateien vollkommen aus.
 */
object TextDiff {

    fun unified(original: String, updated: String, fileName: String = ""): String {
        val before = original.split("\n")
        val after = updated.split("\n")
        val ops = diffOps(before, after)
        if (ops.none { it.first != ' ' }) return ""

        val sb = StringBuilder()
        if (fileName.isNotEmpty()) {
            sb.append("--- a/$fileName\n")
            sb.append("+++ b/$fileName\n")
        }
        ops.forEach { (marker, text) ->
            sb.append(marker).append(text).append('\n')
        }
        return sb.toString()
    }

    /** @return Liste aus (Marker, Zeile) mit ' ', '-' und '+'. */
    fun diffOps(before: List<String>, after: List<String>): List<Pair<Char, String>> {
        val lcs = lcsLengths(before, after)
        val result = mutableListOf<Pair<Char, String>>()
        var i = 0
        var j = 0
        while (i < before.size && j < after.size) {
            when {
                before[i] == after[j] -> {
                    result += ' ' to before[i]
                    i++
                    j++
                }

                lcs[i + 1][j] >= lcs[i][j + 1] -> {
                    result += '-' to before[i]
                    i++
                }

                else -> {
                    result += '+' to after[j]
                    j++
                }
            }
        }
        while (i < before.size) {
            result += '-' to before[i]
            i++
        }
        while (j < after.size) {
            result += '+' to after[j]
            j++
        }
        return result
    }

    private fun lcsLengths(a: List<String>, b: List<String>): Array<IntArray> {
        val table = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) {
            for (j in b.indices.reversed()) {
                table[i][j] = if (a[i] == b[j]) {
                    table[i + 1][j + 1] + 1
                } else {
                    maxOf(table[i + 1][j], table[i][j + 1])
                }
            }
        }
        return table
    }
}
