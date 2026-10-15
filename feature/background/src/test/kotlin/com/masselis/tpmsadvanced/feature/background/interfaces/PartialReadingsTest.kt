package com.masselis.tpmsadvanced.feature.background.interfaces

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

internal class PartialReadingsTest {
    @Test
    fun `a tyre can alert while other tyres remain silent`() = runTest {
        val front = MutableSharedFlow<Int>()
        val rear = MutableSharedFlow<Int>()
        listOf(front, rear).partialReadings().test {
            assertEquals(emptyList(), awaitItem())
            front.emit(50)
            assertEquals(listOf(50), awaitItem())
            rear.emit(200)
            assertEquals(listOf(50, 200), awaitItem())
            front.emit(220)
            assertEquals(listOf(220, 200), awaitItem())
        }
    }
}
