plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val cncVersionCode = providers.gradleProperty("CNC_VERSION_CODE")
    .orElse(providers.environmentVariable("GITHUB_RUN_NUMBER"))
    .orElse("2")
    .get()
    .toInt()

val cncVersionName = providers.gradleProperty("CNC_VERSION_NAME")
    .orElse(
        providers.environmentVariable("GITHUB_RUN_NUMBER")
            .map { "0.2." + it }
    )
    .orElse("0.2.0")
    .get()

val updateManifestUrl = providers.gradleProperty("CNC_UPDATE_MANIFEST_URL")
    .orElse("https://raw.githubusercontent.com/Yoshida99/CNC-Master/main/update/version.json")
    .get()

android {
    namespace = "com.yoshida.cncmaster"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yoshida.cncmaster"
        minSdk = 26
        targetSdk = 37
        versionCode = cncVersionCode
        versionName = cncVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$updateManifestUrl\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
