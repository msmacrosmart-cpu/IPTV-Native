plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Supply a v3 key at build time; never commit credentials. The key is still
// recoverable from a distributed APK, as with any client-side API key.
val tmdbApiKey = providers.environmentVariable("TMDB_API_KEY")
    .orElse(providers.gradleProperty("TMDB_API_KEY"))
    .orElse("").get().trim()
require(tmdbApiKey.isEmpty() || tmdbApiKey.matches(Regex("[a-fA-F0-9]{32}"))) {
    "TMDB_API_KEY must be a 32-character v3 API key"
}

// Installed updates require BOTH a growing versionCode and the same signing key.
val releaseKeystore = providers.environmentVariable("RELEASE_KEYSTORE_PATH").orElse("").get()
val releaseStorePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").orElse("").get()
val releaseAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orElse("").get()
val releaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orElse("").get()
val updateVersionCode = providers.environmentVariable("ANDROID_VERSION_CODE")
    .orElse(providers.environmentVariable("GITHUB_RUN_NUMBER").map { (1000 + it.toInt()).toString() })
    .orElse("10").get().toInt()
require(updateVersionCode in 1..2_100_000_000) { "Invalid Android update versionCode" }
if (releaseKeystore.isNotBlank()) {
    require(listOf(releaseStorePassword, releaseAlias, releaseKeyPassword).all { it.isNotBlank() }) {
        "Persistent release signing requires keystore password, alias and key password"
    }
}

android {
    namespace = "com.example.iptv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.iptv"
        minSdk = 24
        targetSdk = 35
        versionCode = updateVersionCode
        versionName = "2.7.1"
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    if (releaseKeystore.isNotBlank()) signingConfigs.create("persistentRelease") {
        storeFile = file(releaseKeystore)
        storePassword = releaseStorePassword
        keyAlias = releaseAlias
        keyPassword = releaseKeyPassword
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Missing secrets produce a TEST artifact only, never a promised in-place update.
            signingConfig = signingConfigs.getByName(if (releaseKeystore.isNotBlank()) "persistentRelease" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    // ---- AndroidX core, lifecycle & activity ----
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // ---- Jetpack Compose & Material 3 ----
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ---- Media3 / ExoPlayer (HLS, MPEG-TS, DASH, MP4) ----
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")

    // ---- Networking: Retrofit + OkHttp for Xtream Codes player_api.php ----
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // ---- Image loading (channel logos, posters, backdrops) ----
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("com.google.zxing:core:3.5.3")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
