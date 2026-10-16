package com.masselis.tpmsadvanced.interfaces

import com.masselis.tpmsadvanced.data.vehicle.model.AppRelease
import com.masselis.tpmsadvanced.data.vehicle.model.parseReleases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Public release metadata only; no account token, silent install, or background polling. */
internal object GitHubUpdates {
    @Suppress("MagicNumber", "ThrowsCount") // Explicit network and payload bounds.
    suspend fun releases(): List<AppRelease> = withContext(Dispatchers.IO) {
        val result = mutableListOf<AppRelease>()
        for (page in 1..10) {
            ensureActive()
            val connection = URL("https://api.github.com/repos/TerminalAddict/TyrePing/" +
                "releases?per_page=100&page=$page")
                .openConnection() as HttpsURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
                connection.setRequestProperty("User-Agent", "TyrePing-update-checker")
                val status = connection.responseCode
                check(status != 403 && status != 429) { "GitHub rate limit reached. Try again later." }
                check(status == 200) { "GitHub returned HTTP $status. Try again later or open the releases page." }
                val bytes = connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (output.size() <= 2_000_000) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                check(bytes.size <= 2_000_000) { "GitHub response was too large. Open the releases page." }
                ensureActive()
                val entries = parseReleases(bytes.toString(Charsets.UTF_8))
                result += entries
                if (entries.size < 100) return@withContext result
            } finally { connection.disconnect() }
        }
        error("Release history exceeds the check limit. Open the releases page to check manually.")
    }
}
