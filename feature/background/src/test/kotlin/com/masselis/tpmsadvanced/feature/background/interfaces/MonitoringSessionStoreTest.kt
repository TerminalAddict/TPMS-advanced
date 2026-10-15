package com.masselis.tpmsadvanced.feature.background.interfaces

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class MonitoringSessionStoreTest {
    private val values = mutableMapOf<String, Any?>()
    private val editor = mockk<SharedPreferences.Editor> {
        every { putString(any(), any()) } answers { values[firstArg()] = secondArg<String?>(); this@mockk }
        every { putBoolean(any(), any()) } answers { values[firstArg()] = secondArg<Boolean>(); this@mockk }
        every { putLong(any(), any()) } answers { values[firstArg()] = secondArg<Long>(); this@mockk }
        every { putInt(any(), any()) } answers { values[firstArg()] = secondArg<Int>(); this@mockk }
        every { clear() } answers { values.clear(); this@mockk }
        every { commit() } returns true
    }
    private val preferences = mockk<SharedPreferences> {
        every { edit() } returns editor
        every { getString(any(), any()) } answers { values[firstArg()] as String? ?: secondArg() }
        every { getBoolean(any(), any()) } answers { values[firstArg()] as Boolean? ?: secondArg() }
        every { getLong(any(), any()) } answers { values[firstArg()] as Long? ?: secondArg() }
        every { getInt(any(), any()) } answers { values[firstArg()] as Int? ?: secondArg() }
    }
    private val store = MonitoringSessionStore(preferences)
    private val session = MonitoringSession(UUID.randomUUID(), "Bike", false, 10_000, 123, 1, 5_000)

    @Test fun `manual session can be restored after preferences are reopened`() {
        store.started(session)
        assertEquals(session, MonitoringSessionStore(preferences).read())
        verify(exactly = 1) { editor.commit() }
    }

    @Test fun `automatic watch survives without any sensor or trigger packets`() {
        store.started(session.copy(automatic = true))
        assertEquals(session.copy(automatic = true), MonitoringSessionStore(preferences).read())
    }

    @Test fun `switching vehicles restores only the last accepted session`() {
        store.started(session)
        val trailer = session.copy(vehicleId = UUID.randomUUID(), vehicleName = "Trailer", startedAt = 11_000)
        store.started(trailer)
        assertEquals(trailer, MonitoringSessionStore(preferences).read())
    }

    @Test fun `explicit stop is persisted before returning and cannot produce a recovery warning`() {
        store.started(session)
        store.clear()
        assertNull(MonitoringSessionStore(preferences).read())
        verify(exactly = 2) { editor.commit() }
    }

    @Test fun `fresh or damaged session metadata does not prevent launch`() {
        assertNull(store.read())
        values["vehicle"] = "not-a-uuid"
        assertNull(store.read())
    }
}
