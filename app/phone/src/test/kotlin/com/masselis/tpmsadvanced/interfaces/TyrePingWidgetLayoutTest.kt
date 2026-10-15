package com.masselis.tpmsadvanced.interfaces

import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.masselis.tpmsadvanced.R
import org.junit.Rule
import org.junit.Test

internal class TyrePingWidgetLayoutTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig(screenWidth = 280, screenHeight = 320,
        density = Density.MEDIUM, softButtons = false), theme = "android:Theme.Material3.DayNight.NoActionBar")

    @Test fun fiveWheelWidget() {
        val view = paparazzi.inflate<LinearLayout>(R.layout.tyreping_widget)
        view.findViewById<TextView>(R.id.widget_title).text = "Touring car with spare"
        view.findViewById<TextView>(R.id.widget_status).text = "Monitoring car · snapshot"
        view.findViewById<TextView>(R.id.widget_readings).text =
            listOf("front left", "front right", "rear left", "rear right", "spare").joinToString("\n") {
                "$it: 34 psi · 25°C\n  15 Oct 18:24"
            }
        view.findViewById<TextView>(R.id.widget_warning).text = "Maintenance due: Check tyres"
        view.findViewById<TextView>(R.id.widget_reading_age).text = "Oldest reading: 03:12 ago"
        view.findViewById<TextView>(R.id.widget_age).text = "Snapshot age: 00:15"
        paparazzi.snapshot(view)
    }
}
