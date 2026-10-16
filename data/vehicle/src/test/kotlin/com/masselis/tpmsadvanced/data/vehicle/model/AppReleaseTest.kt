package com.masselis.tpmsadvanced.data.vehicle.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

internal class AppReleaseTest {
    private fun release(tag: String, preview: Boolean = false, draft: Boolean = false) = AppRelease(tag,
        prerelease = preview, draft = draft, url = "${AppRelease.REPOSITORY}/releases/tag/$tag",
        assets = listOf(ReleaseAsset("TyrePing-$tag.apk",
            "${AppRelease.REPOSITORY}/releases/download/$tag/TyrePing-$tag.apk",
            "uploaded", 123)))
    @Test fun `stable ignores previews and drafts and compares versions not timestamps`() {
        val releases = listOf(release("v0.9.9"), release("v0.11.0", true), release("v0.10.0"),
            release("v0.12.0-rc.1"), release("v1.0.0", draft = true))
        assertEquals("v0.10.0", newestRelease(releases, false)?.tag)
        assertEquals("v0.12.0-rc.1", newestRelease(releases, true)?.tag)
        assertTrue(isNewerRelease(release("v0.12.0"), "0.11.0-debug"))
        assertFalse(isNewerRelease(release("v0.10.0"), "0.11.0"))
        assertFalse(isNewerRelease(release("v0.11.0"), "0.11.0-debug"))
    }
    @Test fun `semantic prerelease identifiers sort numerically`() {
        val versions = listOf("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta",
            "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0")
        val parsed = versions.map { assertNotNull(ReleaseVersion.parse(it)) }
        assertEquals(parsed, parsed.reversed().sorted())
        assertNull(ReleaseVersion.parse("1.0.0-rc.01"))
        assertNull(ReleaseVersion.parse("999999999999999999999.0.0"))
        assertFailsWith<IllegalArgumentException> { isNewerRelease(release("v1.0.0"), "unknown") }
    }
    @Test fun `only expected repository and signed apk name produce trusted download links`() {
        val valid = release("v0.12.0")
        assertNotNull(valid.apk)
        assertNull(valid.copy(assets = valid.assets.map { it.copy(url = "https://example.com/app.apk") }).apk)
        assertNull(valid.copy(assets = valid.assets.map { it.copy(name = "app-debug.apk") }).apk)
        assertNull(valid.copy(assets = valid.assets.map { it.copy(state = "new") }).apk)
        assertNull(newestRelease(listOf(valid.copy(url = "https://example.com/release")), true))
        assertNull(newestRelease(listOf(release("../../bad")), true))
    }
    @Test fun `response parser handles nullable notes and unknown github fields`() {
        val parsed = parseReleases("""[{"tag_name":"v0.12.0","name":null,"body":null,"prerelease":false,
            "draft":false,"html_url":"https://github.com/TerminalAddict/TyrePing/releases/tag/v0.12.0",
            "assets":[],"immutable":false}]""")
        assertEquals("v0.12.0", newestRelease(parsed, false)?.tag)
        assertNull(parsed.single().apk)
        assertFailsWith<IllegalArgumentException> { parseReleases("not json") }
    }
}
