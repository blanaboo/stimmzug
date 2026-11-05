import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
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

/**
 * Downloads test recordings into a cache outside the repository and verifies their SHA-256. Without a network
 * connection it only warns (the tests that need the files are skipped); a wrong checksum fails the build.
 */
abstract class DownloadTestData : DefaultTask() {
    @get:Input
    abstract val baseUrl: Property<String>

    /** File name → expected SHA-256 (hex). */
    @get:Input
    abstract val checksums: MapProperty<String, String>

    @get:Internal
    abstract val targetDir: DirectoryProperty

    /** `gradlew --offline`: only verify what is there, download nothing. */
    @get:Input
    abstract val offline: Property<Boolean>

    @TaskAction
    fun download() {
        val dir = targetDir.get().asFile.apply { mkdirs() }
        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build()
        val missing = ArrayList<String>()
        for ((name, expected) in checksums.get()) {
            val file = File(dir, name)
            if (file.isFile && sha256(file) == expected) continue
            if (offline.get()) {
                missing += "$name (offline)"
                continue
            }
            val part = File(dir, "$name.part")
            try {
                val uri = URI(baseUrl.get() + name.replace(" ", "%20"))
                val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofFile(part.toPath()))
                if (response.statusCode() != 200) {
                    missing += "$name (HTTP ${response.statusCode()})"
                    continue
                }
                val actual = sha256(part)
                if (actual != expected) {
                    throw GradleException("Prüfsumme falsch für $name: erwartet $expected, erhalten $actual")
                }
                Files.move(part.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (e: java.io.IOException) {
                missing += "$name (${e.javaClass.simpleName}: ${e.message})"
            } finally {
                part.delete()
            }
        }
        if (missing.isNotEmpty()) {
            logger.warn("Testdaten nicht geladen, die Tests dazu werden übersprungen: ${missing.joinToString()}")
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

// University of Iowa MIS tuba recordings (see docs/testdata.md). Kept in testdata/cache/ (gitignored, survives
// `clean`) rather than the Gradle user home: Gradle treats ~/.gradle/caches as immutable and would not notice
// deleted files, so the test would stay UP-TO-DATE instead of being skipped.
val iowaDir = rootDir.resolve("testdata/cache/iowa-tuba-ff")
val iowaChecksums = file("src/test/resources/iowa/tuba-ff.sha256").readLines()
    .filter { it.isNotBlank() && !it.startsWith("#") }
    .associate { line -> line.substringAfter("  ") to line.substringBefore("  ") }

val downloadIowaTuba by tasks.registering(DownloadTestData::class) {
    description = "Lädt die Tuba-Aufnahmen der University of Iowa (ff, C1–C4) für den Regressionstest."
    baseUrl.set("https://theremin.music.uiowa.edu/sound%20files/MIS%20Pitches%20-%202014/Brass/Tuba/")
    checksums.set(iowaChecksums)
    targetDir.set(iowaDir)
    offline.set(gradle.startParameter.isOffline)
}

tasks.test {
    dependsOn(downloadIowaTuba)
    // Re-run the tests when the recordings appear (e.g. first build offline).
    inputs.files(fileTree(iowaDir)).withPropertyName("iowaRecordings").withPathSensitivity(PathSensitivity.NAME_ONLY)
    systemProperty("stimmzug.iowa.dir", iowaDir.absolutePath)
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
    testLogging {
        events("failed", "skipped")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
