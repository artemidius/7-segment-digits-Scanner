# 7-Digits Android

Standalone Android project for the 7-Digits library and its test app.

| Module | Purpose |
| --- | --- |
| `:library` | Android library, published as a release AAR with sources and Maven metadata. |
| `:test-app` | Gallery demo consuming a local `.aar`; shows the image, boxes, and both API outputs. |

The library implements both recognition APIs with a private, coroutine-based
LiteRT CPU pipeline. Supply a compatible model and label metadata as app assets.
Model training/conversion remains in the separate `7-Digits` project.

## Create a recognizer

Place `detector.tflite` and `detector.json` in your app module's
`src/main/assets/seven-digits/`, then call from a coroutine:

```kotlin
val recognizer = SevenDigitsRecognizer.create(context)
```

Creation loads and validates the model off the main thread. Keep this recognizer
for reuse; it retains immutable model bytes, not the context. Each recognition
call owns and releases its native interpreter, including after cancellation.
There is no public `close()` or scope to manage.

The model and metadata from `7-Digits/experiments/local/smart-bp-litert/` work with
this implementation. That research model recognizes digits only, misses the
clock/date content in our monitor examples, and carries upstream AGPL terms.
Model files are supplied separately and are **not bundled in the library AAR**.
See [model setup and runtime details](docs/API.md#model-assets).

The library returns detected numeric text wherever it appears on the display. It
does not identify or parse dates, times, blood pressure, or pulse; those meanings
belong to the client. No date/time filtering is applied to detections.

## Public API

Choose the text or visual entry point inside a coroutine.

**Text API** — rows of strings:

```kotlin
val lines: Lines = recognizer.recognizeText(photo)
// Lines = List<List<String>>
// Example: [["120"], ["80", "65"], ["11.11.2026", "11:05"]]
```

The outer list contains rows from top to bottom. Each inner list contains fields
from left to right. Strings preserve leading zeros, signs, and punctuation.

**Visual API** — the same rows plus bounding boxes, scores, and characters:

```kotlin
val visual: RecognitionResult = recognizer.recognizeVisual(photo)
val boxes = visual.regions.map { it.boundingBox }
val lines: Lines = visual.toLines() // Reuse the result; no second inference.
```

`visual.lines` preserves the row grouping; `visual.regions` is a flattened view for
overlays. Both entry points use the same options and model capabilities.
See [the API contract](docs/API.md)
for rotation, errors, cancellation, and the current implementation status. Each
recognition call owns its resources through structured concurrency; callers use a
suspend function without a manual lifecycle method.

## Build

Open this directory in Android Studio, or use the included Gradle wrapper.
Use JDK 17 or newer (verified with JDK 21), Android SDK Platform 37 and Build Tools
36.0.0. Set `ANDROID_HOME` or let Android Studio create `local.properties`.
The minimum supported Android API is currently 23.

```sh
./gradlew :library:assembleRelease
python3 scripts/prepare_test_app.py
./gradlew :test-app:assembleDebug
./gradlew :library:testDebugUnitTest
./gradlew :library:lintRelease :test-app:lintDebug
```

Outputs:

- `library/build/outputs/aar/library-release.aar`
- `test-app/build/outputs/apk/debug/test-app-debug.apk`

Kotlin support is built into Android Gradle Plugin 9.5.1. Versions are defined in
`gradle/libs.versions.toml`; the wrapper pins Gradle and its distribution checksum.
The runtime uses LiteRT 1.4.2's CPU Interpreter and kotlinx.coroutines 1.11.0;
these implementation dependencies are included in the published Maven metadata.

## Gallery test app

The app uses `implementation(files("libs/seven-digits.aar"))`, with no dependency
on the library Gradle project. Because a bare AAR carries no dependency metadata,
the app declares LiteRT and coroutines explicitly. `prepare_test_app.py` copies the
release AAR into `test-app/libs/` and the converted research model, JSON, and license
into `test-app/src/main/assets/seven-digits/`. These generated inputs are Git-ignored
and survive Gradle `clean`. After changing the library, rebuild its release AAR and
rerun preparation to update the app's copy. The script accepts `--aar`, `--model-dir`,
and `--model-license` for other local inputs. The default model location is the
sibling `7-Digits` research project; it is required on a fresh checkout.

Install/run the `test-app` configuration in Android Studio, or:

```sh
./gradlew :test-app:installDebug
adb shell am start -n dev.sevendigits.testapp/.MainActivity
```

- **Gallery** opens the system document picker for images. No broad storage access
  or camera permission is requested. Cancelling the picker preserves the current result.
- The selected image appears on the same scrollable page. Blue boxes show fields;
  orange boxes show characters. Toggle **Show bounding boxes** to see the plain image.
- **String API · Lines** shows the actual nested string lists. **Visual API · Details**
  shows the full result as JSON, including coordinates, characters, and confidence.
  The sample calls both APIs independently to exercise them; it does not parse or
  label their values. Application code that needs both views can instead use
  `recognizeVisual(...).toLines()` to avoid a second inference.
- **Camera** is visible and disabled for this stage.

Image loading and recognition run off the main thread. Large images are sampled
to a maximum side of 2048 pixels and EXIF rotation/mirroring is applied before
recognition. Both APIs and the preview use this same decoded bitmap, so returned
coordinates refer to it. Activity recreation reloads the selected URI and reruns
recognition; cancellation releases native resources before bitmap memory is reclaimed.
Loading, empty results, inaccessible/invalid images, and recognition errors have
explicit states on the page.

The development APK includes the supplied model and its license; the library AAR
still contains no model. Run app tests with `./gradlew :test-app:connectedDebugAndroidTest`
after preparing the AAR/model and connecting an emulator/device.

Verified on Android 14 / API 34: a photo selected through the actual system picker
displayed `[["126"], ["81"], ["81"]]` and matching visual boxes/details. Three app
instrumentation tests passed, covering empty results through both APIs, picker
cancellation, activity recreation, all eight EXIF orientations, bounded large-image
decoding, and invalid image rejection.

## Android integration tests

Generate test-only assets from the converted research model, then run against a
connected emulator/device. The preparation script checks hashes, retains licenses,
and fetches two pinned MIT-licensed fixture photos:

```sh
python3 scripts/prepare_instrumentation_assets.py
./gradlew :library:connectedDebugAndroidTest
```

Use `--model-dir`, `--model-license`, or `--photos /path/to/bp-tracker/test/fixtures`
to override the inputs. Generated assets live in `library/build/test-assets/`, are
Git-ignored, and are packaged only into the test APK. Run preparation again after
`clean`. Missing test assets fail the device tests rather than silently skipping them.

Verified on an Android 14 / API 34 ARM64 emulator: 19 JVM tests and 6 device tests,
including real-photo text/visual agreement, all right-angle rotations, hardware
bitmaps, blank images, invalid assets, concurrency, and cancellation/reuse. These
are implementation checks on two development photos, not a general OCR benchmark.

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
