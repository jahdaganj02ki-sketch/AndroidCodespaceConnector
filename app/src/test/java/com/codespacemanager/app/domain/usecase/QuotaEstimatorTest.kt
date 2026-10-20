package com.codespacemanager.app.domain.usecase

import com.codespacemanager.app.domain.model.Codespace
import com.codespacemanager.app.domain.model.CodespaceState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuotaEstimatorTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `kerne werden aus der maschine abgeleitet`() {
        assertThat(QuotaEstimator.coresForMachine("basicLinux32gb")).isEqualTo(2)
        assertThat(QuotaEstimator.coresForMachine("standardLinux32gb")).isEqualTo(4)
        assertThat(QuotaEstimator.coresForMachine("premiumLinux64gb")).isEqualTo(8)
        assertThat(QuotaEstimator.coresForMachine("unbekannt")).isEqualTo(2)
        assertThat(QuotaEstimator.coresForMachine(null)).isEqualTo(2)
    }

    @Test
    fun `verbrauch ist kerne mal stunden seit der letzten nutzung`() {
        val codespace = Codespace(
            name = "a",
            repositoryFullName = "o/r",
            state = CodespaceState.AVAILABLE,
            machine = "basicLinux32gb",
            lastUsedAtMillis = now - 3_600_000L,
        )
        assertThat(QuotaEstimator.estimateCoreHours(listOf(codespace), now)).isWithin(0.001).of(2.0)
    }

    @Test
    fun `mehrere codespaces werden aufsummiert`() {
        val codespaces = listOf(
            Codespace(
                name = "a",
                repositoryFullName = "o/r",
                state = CodespaceState.AVAILABLE,
                machine = "basicLinux32gb",
                lastUsedAtMillis = now - 3_600_000L,
            ),
            Codespace(
                name = "b",
                repositoryFullName = "o/r",
                state = CodespaceState.AVAILABLE,
                machine = "standardLinux32gb",
                lastUsedAtMillis = now - 1_800_000L,
            ),
        )
        // 2 * 1h + 4 * 0.5h = 4
        assertThat(QuotaEstimator.estimateCoreHours(codespaces, now)).isWithin(0.001).of(4.0)
    }

    @Test
    fun `formatierung bleibt lesbar`() {
        assertThat(QuotaEstimator.format(2.5)).contains("Core-Std.")
        assertThat(QuotaEstimator.format(25.0)).isEqualTo("25 Core-Std.")
    }
}
