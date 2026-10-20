package com.codespacemanager.app.domain.usecase

import com.codespacemanager.app.domain.model.Codespace
import com.codespacemanager.app.domain.model.CodespaceState

/**
 * Schaetzung des Kontingent-Verbrauchs: Summe (Kerne x Stunden seit `last_used_at`).
 * Die Angabe ist ausdruecklich eine Schaetzung (Maschinen-Aufloesung erfordert einen
 * API-Aufruf pro Repository).
 */
object QuotaEstimator {

    private val MACHINE_CORES = mapOf(
        "basicLinux32gb" to 2,
        "standardLinux32gb" to 4,
        "premiumLinux64gb" to 8,
        "premiumLinux128gb" to 16,
    )

    fun coresForMachine(machine: String?): Int =
        MACHINE_CORES[machine?.trim()] ?: 2

    fun estimateCoreHours(
        codespaces: List<Codespace>,
        now: Long = System.currentTimeMillis(),
    ): Double = codespaces.sumOf { codespace ->
        val lastUsed = codespace.lastUsedAtMillis ?: return@sumOf 0.0
        if (codespace.state == CodespaceState.SHUTDOWN && codespace.lastOpenedAtMillis != null) {
            // Gestoppte Codespaces verbrauchen keine Rechenzeit mehr.
            return@sumOf 0.0
        }
        val hours = (now - lastUsed).coerceAtLeast(0L) / 3_600_000.0
        hours * coresForMachine(codespace.machine)
    }

    fun format(coreHours: Double): String =
        if (coreHours < 10) String.format("%.1f Core-Std.", coreHours)
        else String.format("%.0f Core-Std.", coreHours)
}
