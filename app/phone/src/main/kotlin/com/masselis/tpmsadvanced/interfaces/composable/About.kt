package com.masselis.tpmsadvanced.interfaces.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.BuildConfig
import kotlinx.coroutines.launch

@Composable
internal fun About(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("TyrePing", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Version ${BuildConfig.VERSION_NAME}" + if (BuildConfig.DEBUG) " · Debug" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Bluetooth tyre-pressure monitoring for Android.")
            Text("An independent fork of TPMS Advanced, maintained by Paul Willard (TerminalAddict).")
        }
        AboutLink("TyrePing website", "https://www.tyreping.com/", snackbarHostState)
        AboutLink("Licences", "https://www.tyreping.com/licenses/", snackbarHostState)
        UpdateCheckerButton()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("About Paul Willard", style = MaterialTheme.typography.titleLarge)
            Text(
                "Paul is a systems engineer and business owner based in Waikato, New Zealand, " +
                    "with a love of Linux, motorcycles and the outdoors."
            )
        }
        AboutLink("More about Paul", "https://www.paulwillard.nz/", snackbarHostState)
        Text(
            "Links open in your browser.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AboutLink(title: String, url: String, snackbarHostState: SnackbarHostState) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    Card(
        onClick = {
            try {
                uriHandler.openUri(url)
            } catch (_: IllegalArgumentException) {
                scope.launch { snackbarHostState.showSnackbar("No browser available to open this link.") }
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(url, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
