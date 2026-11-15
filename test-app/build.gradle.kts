plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.sevendigits.testapp"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.sevendigits.testapp"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.scanner)
    // Main dispatcher is used by the app; LiteRT and ML Kit come from the scanner's metadata.
    implementation(libs.coroutines.android)
    implementation(libs.exifinterface)
    implementation(libs.androidx.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
