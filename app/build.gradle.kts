import java.util.Properties

plugins {
    id("com.android.application")
}

// Maps API key: MAPS_API_KEY in local.properties, or -PMAPS_API_KEY=... on the command line.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY")
    ?: providers.gradleProperty("MAPS_API_KEY").orNull
    ?: ""

val playServicesMapsVersion = "20.1.0"

android {
    namespace = "com.example.mapszindex"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.mapszindex"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        manifestPlaceholders["mapsApiKey"] = mapsApiKey
        buildConfigField("String", "PLAY_SERVICES_MAPS_VERSION", "\"$playServicesMapsVersion\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-maps:$playServicesMapsVersion")
}
