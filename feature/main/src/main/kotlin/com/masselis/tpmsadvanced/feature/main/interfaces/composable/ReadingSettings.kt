package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.feature.main.ioc.ReadingSettingsDependencies

@Composable
public fun ReadingSettings() {
    val preferences = (appGraph as ReadingSettingsDependencies).readingPreferences
    val enabled by preferences.detectLeaks.collectAsState()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Experimental leak detection", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { preferences.detectLeaks.value = it })
        }
        Text("Warn about repeated pressure loss while readings are live. Cooling and long gaps reset the trend. " +
            "This experiment can miss leaks or warn unnecessarily; keep your normal pressure alerts enabled.")
    }
}
