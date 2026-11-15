# 7-Digits Android

Standalone Android project for the 7-Digits library and its test app.

| Module | Purpose |
| --- | --- |
| `:library` | Android library, published as a release AAR with sources and Maven metadata. |
| `:test-app` | Minimal Android app depending on the library; launches an empty `MainActivity`. |

The library contains the public Kotlin recognition contract and validated result
types. The LiteRT implementation, recognizer factory, and model assets are the next
step. Model experiments remain in the separate `7-Digits` project.

## Public API

```kotlin
import android.graphics.Bitmap
import dev.sevendigits.SevenDigitsRecognizer

// The recognizer implementation will be supplied by the runtime integration.
suspend fun readDisplay(recognizer: SevenDigitsRecognizer, photo: Bitmap): List<String> {
    val result = recognizer.recognize(photo)
    return result.regions.map { it.text }
}
```

Each region exposes its text, original-image bounding box, minimum character score,
and individual characters. The API preserves leading zeros and punctuation and
reports the loaded model's supported characters. See [the API contract](docs/API.md)
for rotation, errors, cancellation, lifecycle, and the current implementation status.

## Build

Open this directory in Android Studio, or use the included Gradle wrapper.
Use JDK 17 or newer (verified with JDK 21), Android SDK Platform 37 and Build Tools
36.0.0. Set `ANDROID_HOME` or let Android Studio create `local.properties`.
The minimum supported Android API is currently 23.

```sh
./gradlew :library:assembleRelease :test-app:assembleDebug
./gradlew :library:testDebugUnitTest
./gradlew :library:lintRelease :test-app:lintDebug
```

Outputs:

- `library/build/outputs/aar/library-release.aar`
- `test-app/build/outputs/apk/debug/test-app-debug.apk`

Kotlin support is built into Android Gradle Plugin 9.5.1. Versions are defined in
`gradle/libs.versions.toml`; the wrapper pins Gradle and its distribution checksum.

## Publish and distribute

Development coordinates are `dev.sevendigits:seven-digits:0.1.0-SNAPSHOT`.
Group, artifact, and version are configured in `gradle.properties` and can be
overridden with `-PlibraryGroup=...`, `-PlibraryArtifact=...`, and
`-PlibraryVersion=...`.

Publish this library to a local Maven repository in `build/maven-repository/`:

```sh
./gradlew :library:publishReleasePublicationToLocalDistributionRepository
```

That directory contains the AAR, sources JAR, POM, Gradle module metadata, and
checksums. It can be zipped for distribution or served as a Maven repository.
Prefer this over a bare AAR so consumers receive dependency metadata.

Alternatively, publish into your machine's Maven local repository:

```sh
./gradlew :library:publishToMavenLocal
```

To upload to a standard Maven repository that accepts Gradle publication, supply
its URL and credentials. Keep credentials in `~/.gradle/gradle.properties`:

```properties
remoteUsername=YOUR_USERNAME
remotePassword=YOUR_TOKEN
```

Then run:

```sh
./gradlew :library:publishReleasePublicationToRemoteRepository \
  -PmavenRepositoryUrl=https://YOUR_MAVEN_HOST/repository/releases \
  -PlibraryGroup=YOUR_VERIFIED_GROUP \
  -PlibraryVersion=0.1.0
```

For CI, credentials can be supplied as `ORG_GRADLE_PROJECT_remoteUsername` and
`ORG_GRADLE_PROJECT_remotePassword` environment variables. Maven Central's portal
upload/signing workflow is not configured: the final publisher namespace, public
repository URL, license, and release metadata need to be chosen first.

## Repository

This directory is independent of the model-research repository. Build outputs,
IDE state, local SDK paths, and signing keys are ignored. The Gradle wrapper is
included so a checkout can build without a system Gradle installation.
