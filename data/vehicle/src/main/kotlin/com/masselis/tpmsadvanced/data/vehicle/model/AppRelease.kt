@file:Suppress("MagicNumber") // Semantic-version component and response-size limits.

package com.masselis.tpmsadvanced.data.vehicle.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

public data class ReleaseVersion(val major: Long, val minor: Long, val patch: Long, val pre: List<String>) :
    Comparable<ReleaseVersion> {
    @Suppress("ReturnCount") // Semantic versions compare core, release rank, then the first different identifier.
    override fun compareTo(other: ReleaseVersion): Int {
        val core = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
        if (core != 0) return core
        if (pre.isEmpty() || other.pre.isEmpty()) return when {
            pre.isEmpty() && other.pre.isEmpty() -> 0; pre.isEmpty() -> 1; else -> -1
        }
        pre.zip(other.pre).forEach { (left, right) ->
            val comparison = compareIdentifier(left, right)
            if (comparison != 0) return comparison
        }
        return pre.size.compareTo(other.pre.size)
    }
    private fun compareIdentifier(left: String, right: String): Int {
        val a = left.toLongOrNull(); val b = right.toLongOrNull()
        return when {
            a != null && b != null -> a.compareTo(b)
            a != null -> -1; b != null -> 1; else -> left.compareTo(right)
        }
    }
    public companion object {
        @Suppress("ReturnCount") // Reject malformed or overflowing remote version components without throwing.
        public fun parse(text: String): ReleaseVersion? {
            val match = Regex("^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)" +
                "(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")
                .matchEntire(text) ?: return null
            val numbers = (1..3).map { match.groupValues[it].toLongOrNull() ?: return null }
            val pre = match.groupValues[4].takeIf { it.isNotEmpty() }?.split('.').orEmpty()
            if (pre.any { it.isEmpty() || invalidNumericIdentifier(it) }) return null
            return ReleaseVersion(numbers[0], numbers[1], numbers[2], pre)
        }
        private fun invalidNumericIdentifier(text: String): Boolean = text.all(Char::isDigit) &&
            (text.toLongOrNull() == null || text.length > 1 && text.startsWith('0'))
    }
}

@Serializable
public data class ReleaseAsset(val name: String, @SerialName("browser_download_url") val url: String,
    val state: String = "", val size: Long = 0)

@Serializable
public data class AppRelease(@SerialName("tag_name") val tag: String, val name: String? = null,
    val body: String? = null, val draft: Boolean = false, val prerelease: Boolean = false,
    @SerialName("html_url") val url: String, val assets: List<ReleaseAsset> = emptyList()) {
    public val version: ReleaseVersion? get() = ReleaseVersion.parse(tag)
    public val apk: ReleaseAsset? get() = assets.firstOrNull {
        it.name == "TyrePing-$tag.apk" && it.state == "uploaded" && it.size > 0 &&
            it.url == "$REPOSITORY/releases/download/$tag/${it.name}"
    }
    public val trusted: Boolean get() = version != null && url == "$REPOSITORY/releases/tag/$tag"
    public companion object { public const val REPOSITORY: String = "https://github.com/TerminalAddict/TyrePing" }
}

private val releaseJson = Json { ignoreUnknownKeys = true }

public fun parseReleases(json: String): List<AppRelease> {
    require(json.length <= 2_000_000) { "GitHub response was too large" }
    return releaseJson.decodeFromString<List<AppRelease>>(json)
}

public fun newestRelease(releases: List<AppRelease>, includePrereleases: Boolean): AppRelease? = releases
    .filter { !it.draft && it.trusted && (includePrereleases || !it.prerelease && it.version?.pre?.isEmpty() == true) }
    .maxByOrNull { checkNotNull(it.version) }

public fun isNewerRelease(release: AppRelease, installed: String): Boolean {
    val current = requireNotNull(ReleaseVersion.parse(installed.removeSuffix("-debug"))) {
        "Installed version could not be compared. Open the releases page to check manually."
    }
    return checkNotNull(release.version) > current
}
