import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Replay CLI: runs recordings (WAV/AIFF) through the DSP chain and writes CSV + PNG. Desktop tool, not shipped.
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":core:dsp"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("io.github.blanaboo.stimmzug.tools.replay.MainKt")
    applicationName = "replay"
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
}

// `./gradlew :tools:replay:run --args="..."` resolves relative paths against the repository root.
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
    testLogging {
        events("failed", "skipped")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
