import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing: environment variables (CI) take precedence over a local, git-ignored keystore.properties.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(env: String, property: String): String? =
    providers.environmentVariable(env).orNull ?: keystoreProperties.getProperty(property)

android {
    namespace = "io.github.blanaboo.stimmzug"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.blanaboo.stimmzug"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("stimmzug.versionCode").get().toInt()
        versionName = providers.gradleProperty("stimmzug.versionName").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val storePath = signingValue("STIMMZUG_KEYSTORE_PATH", "storeFile")
        if (storePath != null) {
            create("release") {
                storeFile = file(storePath)
                storePassword = signingValue("STIMMZUG_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("STIMMZUG_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("STIMMZUG_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkDependencies = true
        // Dependency updates are handled by Dependabot.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

dependencies {
    implementation(project(":core:dsp"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // Compose UI test pulls in espresso-core 3.5.0, which calls InputManager.getInstance (removed in API 37).
    androidTestImplementation(libs.androidx.test.espresso.core)
}
