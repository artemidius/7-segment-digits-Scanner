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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":library"))
}
