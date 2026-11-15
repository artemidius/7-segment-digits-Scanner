buildscript {
    dependencies {
        // LiteRT 2.3.2 carries Kotlin 2.4 metadata; upgrade AGP's built-in compiler.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
}
