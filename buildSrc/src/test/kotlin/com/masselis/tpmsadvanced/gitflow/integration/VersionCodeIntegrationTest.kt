package com.masselis.tpmsadvanced.gitflow.integration

import com.masselis.tpmsadvanced.gitflow.testutil.GitFixture
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VersionCodeIntegrationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun fixture(): GitFixture = GitFixture.create(tmp.root).also {
        File(it.projectDir, "settings.gradle.kts").writeText("")
        File(it.projectDir, "build.gradle.kts").writeText(
            """
            import StricSemanticVersion
            plugins { id("gitflow") }
            gitflow { version.set(StricSemanticVersion("0.5.0")) }
            // Resolve during configuration, as an Android application does for defaultConfig.
            val code = gitflow.versionCode.get()
            tasks.register("printVersionCode") {
                inputs.property("versionCode", code)
                doLast { println("VERSION_CODE=" + inputs.properties["versionCode"]) }
            }
            """.trimIndent()
        )
    }

    private fun build(fixture: GitFixture): BuildResult = GradleRunner.create()
        .withProjectDir(fixture.projectDir)
        .withPluginClasspath()
        .withArguments("printVersionCode", "--configuration-cache", "--console=plain")
        .build()

    private fun assertCode(result: BuildResult, expected: Int) {
        assertTrue(result.output, "VERSION_CODE=$expected" in result.output)
        assertFalse("Version calculation must not run a failing Git command:\n${result.output}",
            "fatal:" in result.output)
    }

    @Test
    fun `ordinary branches and detached HEAD do not require a release ref`() {
        val fixture = fixture()
        // There is deliberately no origin/release/0.5.0 in this repository.
        fixture.checkoutNew("tyreping")
        assertCode(build(fixture), 500000)
        val cached = build(fixture)
        assertCode(cached, 500000)
        assertTrue(cached.output, "Reusing configuration cache" in cached.output)

        fixture.checkout("main")
        assertCode(build(fixture), 500999)
        fixture.checkoutDetached(fixture.rev("main"))
        assertCode(build(fixture), 500000)
    }

    @Test
    fun `release count changes invalidate cached version and switching branch clears suffix`() {
        val fixture = fixture()
        fixture.checkoutNew("release/0.5.0")
        fixture.commit("First release commit")
        fixture.pushNewBranch("release/0.5.0")
        assertCode(build(fixture), 500001)

        fixture.commit("Second release commit")
        fixture.push("release/0.5.0")
        assertCode(build(fixture), 500002)

        fixture.checkout("develop")
        assertCode(build(fixture), 500000)
    }
}
