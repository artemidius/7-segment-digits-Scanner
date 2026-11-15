plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

group = providers.gradleProperty("libraryGroup").get()
version = providers.gradleProperty("libraryVersion").get()

kotlin {
    explicitApi()
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) { into("META-INF") }
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.litert)
    implementation(libs.mlkit.text)
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
                description.set("On-device Android OCR for seven-segment displays and printed Latin text.")
                url.set("https://github.com/artemidius/7-segment-digits-Scanner")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("artemidius")
                        name.set("artemidius")
                        url.set("https://github.com/artemidius")
                    }
                }
                scm {
                    url.set("https://github.com/artemidius/7-segment-digits-Scanner")
                    connection.set("scm:git:https://github.com/artemidius/7-segment-digits-Scanner.git")
                    developerConnection.set("scm:git:ssh://git@github.com/artemidius/7-segment-digits-Scanner.git")
                }
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
