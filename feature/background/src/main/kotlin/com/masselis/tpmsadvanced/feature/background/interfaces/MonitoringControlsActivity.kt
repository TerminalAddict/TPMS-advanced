package com.masselis.tpmsadvanced.feature.background.interfaces

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import com.masselis.tpmsadvanced.core.ui.DarkColors
import com.masselis.tpmsadvanced.core.ui.LightColors
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.MonitoringControls

public class MonitoringControlsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
                MonitoringControls { finish() }
            }
        }
    }
}
