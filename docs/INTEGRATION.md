# Integrate the library

Android API 23+, Kotlin, and a compatible LiteRT detector are required. Recognition
is offline. The library does not request Android permissions or open a camera.

## Install

### Local AAR

Download the AAR from [Releases](https://github.com/artemidius/7-segment-digits-Scanner/releases)
or build `:library:assembleRelease`. Copy it into your app's `libs/` directory.
A bare AAR does not carry transitive dependency metadata:

```kotlin
dependencies {
    implementation(files("libs/seven-segment-digits-scanner-0.1.0-alpha.1.aar"))
    implementation("com.google.ai.edge.litert:litert:1.4.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
```

### Maven bundle

The release `*-maven.zip` contains a Maven repository with the AAR, source JAR,
POM, Gradle module metadata, and dependency versions. Extract it into
`vendor/7-digits/` in your project, then add this repository in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("$rootDir/vendor/7-digits/maven") }
    }
}
```

```kotlin
dependencies {
    implementation("io.github.artemidius:seven-segment-digits-scanner:0.1.0-alpha.1")
}
```

These coordinates are in the downloadable bundle, **not Maven Central or JitPack**.
Alternatively, build and publish locally with
`./gradlew :library:publishToMavenLocal`, then add `mavenLocal()` for development.

## Add a model

Place your licensed model and its class map at:

```text
app/src/main/assets/seven-digits/detector.tflite
app/src/main/assets/seven-digits/detector.json
```

See [model setup](MODELS.md) for the exact tensor contract and an optional research
baseline. A random OCR model or single-digit classifier is not interchangeable
with this whole-image detector. The library AAR contains no trained weights.

## Recognize an image

Call from a coroutine owned by your Activity, ViewModel, or another application
scope. Creation, preprocessing, and inference move work off the calling thread:

```kotlin
import dev.sevendigits.Lines
import dev.sevendigits.SevenDigitsRecognizer

// Inside a coroutine. Reuse the recognizer for subsequent photos.
val recognizer = SevenDigitsRecognizer.create(applicationContext)
val lines: Lines = recognizer.recognizeText(bitmap)
// Example: listOf(listOf("126"), listOf("81"), listOf("81"))
```

`Lines` is exactly `List<List<String>>`: rows top to bottom, fields left to right.
Leading zeros and detected punctuation are retained. Values carry no medical,
time, or date semantics. The client interprets them.

For overlays and text from a single inference:

```kotlin
val visual = recognizer.recognizeVisual(bitmap)
val lines = visual.toLines()
for (region in visual.regions) {
    val text = region.text
    val box = region.boundingBox
    val characters = region.characters
    val minimumScore = region.minimumCharacterConfidence
    // Draw the box after applying the same image-to-view transform as your image.
}
```

Boxes use **input bitmap pixels**, not view pixels. Keep aspect ratio and account
for image padding when drawing. The demo's
[RecognitionImageView](../test-app/src/main/kotlin/dev/sevendigits/testapp/RecognitionImageView.kt)
shows this transformation and optional character boxes.

## Orientation, ownership, and errors

```kotlin
val result = recognizer.recognizeVisual(
    bitmap,
    dev.sevendigits.RecognitionOptions(
        rotationDegrees = 90, // Clockwise correction; 0, 90, 180, or 270.
        minimumCharacterConfidence = 0.25f,
    ),
)
```

The result's boxes still refer to the supplied bitmap. Apply EXIF mirroring before
calling the library. If the decoder already makes the image upright, use rotation 0.

- Keep the bitmap unchanged and unrecycled until the call completes. After
  cancellation, join the job before recycling; native inference may finish first.
- `RecognitionException` means an operational/model failure. Empty `Lines` is a
  successful result with no detections; it is not an error substitute.
- Invalid inputs/options raise `IllegalArgumentException`.
- Always propagate `CancellationException` when catching exceptions in a coroutine.
- The recognizer has no `close()` method. Each call releases its native interpreter
  automatically and independent calls may run concurrently.

Do not treat confidence as a calibrated probability or infer completeness from a
successful result. Detection accuracy depends on the supplied model and image.
See the [complete API contract](API.md).

## Try the demo

Follow [development setup](DEVELOPMENT.md). The demo consumes a copied AAR, opens
the system gallery/camera, and displays the selected image, boxes, raw `Lines`, and
the full visual result. It calls both APIs independently for comparison.
