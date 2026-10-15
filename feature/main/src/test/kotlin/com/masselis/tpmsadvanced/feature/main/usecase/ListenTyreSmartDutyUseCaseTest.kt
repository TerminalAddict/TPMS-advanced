package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

internal class ListenTyreSmartDutyUseCaseTest {
    @Test
    fun `first reading does not restart scanning and unchanged readings keep arriving`() = runTest {
        val reading = mockk<Tyre.Located>()
        var starts = 0
        var stops = 0
        val broadcasts = flow {
            starts++
            try {
                repeat(3) { emit(reading) }
            } finally {
                stops++
            }
        }
        val scanner = mockk<LocatedTyreScannerUseCase> {
            every { normalScan() } returns broadcasts
            every { highDutyScan() } returns broadcasts
        }

        val received = ListenTyreSmartDutyUseCase(scanner).listen().take(3).toList()

        assertEquals(listOf(reading, reading, reading), received)
        assertEquals(1, starts)
        assertEquals(1, stops)
    }
}
