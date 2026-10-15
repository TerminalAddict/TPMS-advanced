package com.masselis.tpmsadvanced.feature.background.interfaces

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class AlertCadenceTest {
    private val cue = AlertCue("sensor:pressure:false", "front tyre pressure", false, 100.0)
    private val options = AlertOptions(sound = AlertSound.TONES, repeatSeconds = 60)
    @Test fun `fresh packets do not reset repeat timer`() {
        val schedule = AlertCadence()
        assertEquals(listOf(cue), schedule.due(listOf(cue), options, 0, 0))
        assertTrue(schedule.due(listOf(cue.copy(receivedAt = 101.0)), options, 1_000, 0).isEmpty())
        assertEquals(1, schedule.due(listOf(cue), options, 60_000, 0).size)
    }
    @Test fun `silence includes new critical alerts and expires`() {
        val schedule = AlertCadence()
        val critical = cue.copy(key = "critical", critical = true)
        assertTrue(schedule.due(listOf(critical), options, 10, 600_000).isEmpty())
        assertEquals(listOf(critical), schedule.due(listOf(critical), options, 600_000, 600_000))
    }
    @Test fun `clearing permits a later recurrence to announce immediately`() {
        val schedule = AlertCadence()
        schedule.due(listOf(cue), options, 0, 0)
        schedule.due(emptyList(), options, 1_000, 0)
        assertEquals(listOf(cue), schedule.due(listOf(cue), options, 2_000, 0))
    }
    @Test fun `critical escalation bypasses ordinary cadence`() {
        val schedule = AlertCadence()
        schedule.due(listOf(cue), options, 0, 0)
        val critical = cue.copy(key = "critical", critical = true)
        assertEquals(listOf(critical), schedule.due(listOf(cue, critical), options, 10, 0))
        assertEquals(listOf(critical), schedule.due(listOf(cue, critical), options, 20_010, 0))
    }
    @Test fun `disabled alerts do not consume announcements and vibration works without sound`() {
        val schedule = AlertCadence()
        assertTrue(schedule.due(listOf(cue), AlertOptions(), 0, 0).isEmpty())
        assertEquals(listOf(cue), schedule.due(listOf(cue), AlertOptions(vibrate = true), 10, 0))
    }
}
