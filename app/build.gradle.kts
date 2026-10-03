import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.riftdeck"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.riftdeck"
        minSdk = 29
        targetSdk = 36
        versionCode = providers.gradleProperty("appVersionCode").orElse("1").get().toInt().also {
            require(it in 1..2_100_000_000) { "appVersionCode must be a positive Android version code" }
        }
        versionName = providers.gradleProperty("appVersionName").orElse("0.1.0").get().also {
            require(it.matches(Regex("(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})"))) {
                "appVersionName must be a stable major.minor.patch version"
            }
        }

        testInstrumentationRunner = if (providers.gradleProperty("updateUiFixtures").orNull == "true") {
            "com.riftdeck.UpdateUiFixtureRunner"
        } else {
            "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // Keep the key outside Git. Environment configuration takes precedence as a whole.
    val environmentKeyStore = providers.environmentVariable("RIFTDECK_SIGNING_KEYSTORE").orNull
    val localSigningText = if (environmentKeyStore == null) {
        providers.fileContents(rootProject.layout.projectDirectory.file("signing.properties")).asText.orNull
    } else {
        null
    }
    val localSigning = Properties().apply {
        localSigningText?.reader()?.use { load(it) }
    }
    val fixedSigning = if (environmentKeyStore != null || localSigningText != null) {
        fun signingValue(property: String, environment: String): String {
            val value = if (environmentKeyStore != null) {
                providers.environmentVariable(environment).orNull
            } else {
                localSigning.getProperty(property)
            }
            require(!value.isNullOrBlank()) {
                "Missing signing configuration: set $environment or signing.properties $property in the selected source"
            }
            return value
        }
        val keyStorePath = signingValue("storeFile", "RIFTDECK_SIGNING_KEYSTORE")
        val keyStoreFile = if (keyStorePath.startsWith("~/")) {
            File(System.getProperty("user.home"), keyStorePath.removePrefix("~/"))
        } else {
            rootProject.file(keyStorePath)
        }
        require(keyStoreFile.isFile && keyStoreFile.canRead()) {
            "Configured signing keystore is missing or unreadable; check RIFTDECK_SIGNING_KEYSTORE or signing.properties storeFile"
        }
        signingConfigs.create("fixedSigning") {
            storeFile = keyStoreFile
            storePassword = signingValue("storePassword", "RIFTDECK_SIGNING_STORE_PASSWORD")
            keyAlias = signingValue("keyAlias", "RIFTDECK_SIGNING_KEY_ALIAS")
            keyPassword = signingValue("keyPassword", "RIFTDECK_SIGNING_KEY_PASSWORD")
        }
    } else {
        null
    }
    buildTypes {
        debug {
            if (fixedSigning != null) signingConfig = fixedSigning
        }
        release {
            if (fixedSigning != null) signingConfig = fixedSigning
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    sourceSets.getByName("androidTest").assets.srcDir(layout.buildDirectory.dir("generated/update-fixtures-assets"))

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.coil.compose)
    implementation("androidx.media3:media3-exoplayer:1.9.2")
    ksp(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.junit)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// Small manifest-only APKs exercise PackageManager certificate and version validation.
val generateUpdateTestFixtures by tasks.registering(Exec::class) {
    dependsOn("validateSigningDebug")
    val debugSigning = android.buildTypes.getByName("debug").signingConfig!!
    val output = layout.buildDirectory.dir("generated/update-fixtures-assets/update-fixtures")
    inputs.file(rootProject.file("tools/test-fixtures/make_update_apks.py"))
    inputs.file(debugSigning.storeFile!!)
    inputs.property("keyAlias", debugSigning.keyAlias!!)
    inputs.property("versionCode", android.defaultConfig.versionCode!!)
    inputs.property("versionName", android.defaultConfig.versionName!!)
    outputs.dir(output)
    environment("RIFTDECK_FIXTURE_STORE_PASSWORD", debugSigning.storePassword!!)
    environment("RIFTDECK_FIXTURE_KEY_PASSWORD", debugSigning.keyPassword!!)
    commandLine("python3", rootProject.file("tools/test-fixtures/make_update_apks.py"),
        "--sdk", android.sdkDirectory, "--keystore", debugSigning.storeFile!!,
        "--key-alias", debugSigning.keyAlias!!,
        "--base-code", android.defaultConfig.versionCode!!, "--base-version", android.defaultConfig.versionName!!,
        "--output", output.get().asFile)
}
tasks.matching { it.name in setOf("mergeDebugAndroidTestAssets", "generateDebugAndroidTestLintModel",
    "lintAnalyzeDebugAndroidTest") }.configureEach {
    dependsOn(generateUpdateTestFixtures)
}
