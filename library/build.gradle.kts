plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

group = providers.gradleProperty("libraryGroup").get()
version = providers.gradleProperty("libraryVersion").get()

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.litert)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}

android {
    namespace = "dev.sevendigits"
    compileSdk = libs.versions.compileSdk.get().toInt()
    sourceSets.getByName("androidTest").assets.srcDir(layout.buildDirectory.dir("test-assets").get().asFile)

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = providers.gradleProperty("libraryArtifact").get()
            version = project.version.toString()

            pom {
                name.set("7-Digits")
                description.set("Android library for seven-segment display recognition.")
            }

            afterEvaluate {
                from(components["release"])
            }
        }
    }

    repositories {
        maven {
            name = "localDistribution"
            url = rootProject.layout.buildDirectory.dir("maven-repository").get().asFile.toURI()
        }

        providers.gradleProperty("mavenRepositoryUrl").orNull?.let { repositoryUrl ->
            maven {
                name = "remote"
                url = uri(repositoryUrl)
                credentials(PasswordCredentials::class)
            }
        }
    }
}
