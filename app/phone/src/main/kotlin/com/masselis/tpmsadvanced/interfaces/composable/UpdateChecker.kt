@file:Suppress("LongMethod", "TooGenericExceptionCaught")

package com.masselis.tpmsadvanced.interfaces.composable

import android.content.Context
import androidx.core.content.edit
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import com.masselis.tpmsadvanced.BuildConfig
import com.masselis.tpmsadvanced.data.vehicle.model.AppRelease
import com.masselis.tpmsadvanced.data.vehicle.model.isNewerRelease
import com.masselis.tpmsadvanced.data.vehicle.model.newestRelease
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.RideToolsDialog
import com.masselis.tpmsadvanced.interfaces.GitHubUpdates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun UpdateCheckerButton() {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("Check for TyrePing updates") }
    if (open) UpdateChecker { open = false }
}

@Composable
@Suppress("CyclomaticComplexMethod") // Channel, network-result and optional-asset presentation states.
private fun UpdateChecker(close: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("UPDATE_CHECKER", Context.MODE_PRIVATE) }
    var preview by remember { mutableStateOf(preferences.getBoolean("prereleases", false)) }
    var latest by remember { mutableStateOf<AppRelease?>(null) }
    var newer by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Press Check now to contact GitHub.") }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    fun openLink(url: String) {
        try { uri.openUri(url) }
        catch (_: IllegalArgumentException) { message = "No browser is available to open this link." }
    }
    RideToolsDialog("TyrePing updates", close) {
        Text("Installed: ${BuildConfig.VERSION_NAME}")
        FlowRow {
            FilterChip(!preview, enabled = !busy, onClick = {
                preview = false; preferences.edit { putBoolean("prereleases", false) }; latest = null
                message = "Stable channel selected. Check now to refresh."
            }, label = { Text("Stable") })
            FilterChip(preview, enabled = !busy, onClick = {
                preview = true; preferences.edit { putBoolean("prereleases", true) }; latest = null
                message = "Stable and pre-release channel selected. Check now to refresh."
            }, label = { Text("Include pre-releases") })
        }
        Text("Checks published releases on TerminalAddict/TyrePing. Downloads open in your browser; " +
            "installation is your choice. Pre-releases may still need phone testing.")
        if (BuildConfig.DEBUG) Text("This is TyrePing Debug. GitHub APKs install/update signed TyrePing as a " +
            "separate app; they do not replace Debug. Use configuration backup/restore to transfer settings.")
        TextButton(enabled = !busy, onClick = { scope.launch {
            busy = true; latest = null
            try {
                val release = newestRelease(GitHubUpdates.releases(), preview)
                latest = release
                newer = release?.let { isNewerRelease(it, BuildConfig.VERSION_NAME) } == true
                message = when {
                    release == null -> "No published releases found for this channel."
                    newer -> "A newer version is available: ${release.tag}."
                    else -> "No newer version in this channel. Latest published: ${release.tag}."
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = "Could not check for updates: " +
                (failure.message ?: "check your internet connection and try again.") }
            finally { busy = false }
        } }) { Text(if (busy) "Checking…" else "Check now") }
        Text(message)
        latest?.let { release ->
            Text("${release.name ?: release.tag} · ${if (release.prerelease) "Pre-release" else "Stable"}")
            if (newer) release.apk?.let { apk ->
                TextButton(onClick = { openLink(apk.url) }) { Text("Download ${apk.name}") }
            } ?: Text("No matching signed APK is attached yet. Check the release page.")
            TextButton(onClick = { openLink(release.url) }) { Text("Open release page") }
            Text(release.body?.takeIf { it.isNotBlank() } ?: "No release notes provided.")
        }
        TextButton(onClick = { openLink("${AppRelease.REPOSITORY}/releases") }) { Text("Browse all releases") }
    }
}
