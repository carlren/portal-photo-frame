import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun localConfig(name: String): String = localProperties.getProperty(name, "")

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.carlren.photoframe"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.carlren.photoframe"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "SMB_DEFAULT_HOST", localConfig("photoFrame.smb.host").asBuildConfigString())
        buildConfigField("String", "SMB_DEFAULT_SHARE", localConfig("photoFrame.smb.share").asBuildConfigString())
        buildConfigField("String", "SMB_DEFAULT_PATH", localConfig("photoFrame.smb.path").asBuildConfigString())
        buildConfigField("String", "SMB_DEFAULT_USERNAME", localConfig("photoFrame.smb.username").asBuildConfigString())
        buildConfigField("String", "SMB_FALLBACK_HOST", localConfig("photoFrame.smb.fallbackHost").asBuildConfigString())
        buildConfigField("String", "WEATHER_LOCATION_NAME", localConfig("photoFrame.weather.locationName").asBuildConfigString())
        buildConfigField("String", "WEATHER_LATITUDE", localConfig("photoFrame.weather.latitude").asBuildConfigString())
        buildConfigField("String", "WEATHER_LONGITUDE", localConfig("photoFrame.weather.longitude").asBuildConfigString())
        buildConfigField("String", "DISPLAY_TIME_ZONE", localConfig("photoFrame.display.timeZone").asBuildConfigString())
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.12.0")
    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.7.0")
    // EncryptedSharedPreferences for credential storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // SMBJ for Synology SMB2/3 - brings its own bcprov-jdk18on
    implementation("com.hierynomus:smbj:0.12.1")
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
