// Root build: plugin versions are declared once here so that all modules share one classloader.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt) apply false
}

// detekt for every module with Kotlin sources (AGP 9 compiles Kotlin itself, without the Kotlin plugin), see
// docs/adr/0004-static-analysis-detekt.md. `check` runs the analysis with type resolution per source set; it finds
// more (e.g. unsafe `!!` calls), and the plain `detekt` task the plugin wires into `check` would only repeat a subset.
val detektChecks = mapOf(
    "org.jetbrains.kotlin.jvm" to listOf("detektMain", "detektTest"),
    "com.android.application" to listOf("detektDebug", "detektDebugUnitTest", "detektDebugAndroidTest")
)
subprojects {
    detektChecks.forEach { (pluginId, checkTasks) ->
        pluginManager.withPlugin(pluginId) {
            pluginManager.apply("dev.detekt")
            extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
                buildUponDefaultConfig = true
                config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            }
            tasks.named("check") {
                setDependsOn(dependsOn.filterNot { it is TaskProvider<*> && it.name == "detekt" })
                dependsOn(checkTasks)
            }
        }
    }
}

spotless {
    // LF everywhere (see .gitattributes); avoids scanning git attributes / locked files under .gradle.
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint(libs.versions.ktlint.get())
    }
}
