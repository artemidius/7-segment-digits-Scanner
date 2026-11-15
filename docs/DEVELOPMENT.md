# Development

## Prerequisites

- JDK 21 recommended (AGP minimum 17).
- Android SDK platform `platforms;android-37.0`, build-tools `36.0.0`, platform-tools.
- Python 3 for asset/build scripts; Python 3.12 only for optional model conversion.
- Android API 23+ device/emulator for instrumentation tests.

Set `ANDROID_HOME` or let Android Studio generate ignored `local.properties`.
The wrapper pins Gradle 9.9.0 and its distribution checksum; AGP 9.5.1 supplies
built-in Kotlin support. A system Gradle installation is unnecessary.

## Fresh checkout: library and model-free app

```sh
git clone https://github.com/artemidius/7-segment-digits-Scanner.git
cd 7-segment-digits-Scanner
./gradlew :library:testDebugUnitTest :library:lintRelease :library:assembleRelease
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/prepare_test_app.py --aar-only
./gradlew :test-app:assembleDebug :test-app:lintDebug
```

The demo imports `test-app/libs/seven-digits.aar`, **not** `project(":library")`.
After changing the library, rebuild its release AAR and rerun preparation.
`--aar-only` permits a clean build without downloading any model; recognition then
needs model assets. This flag does not remove any model assets already installed.

## Working demo and real-model tests

First follow [model setup](MODELS.md), then:

```sh
python3 scripts/prepare_test_app.py
python3 scripts/prepare_instrumentation_assets.py
./gradlew :test-app:installDebug
adb shell am start -n dev.sevendigits.testapp/.MainActivity
./gradlew :library:connectedDebugAndroidTest :test-app:connectedDebugAndroidTest
```

Gallery and Camera return to the same page with the photo, an optional box overlay,
raw nested string lists, and visual-result JSON. No medical/date/time interpretation
is applied. Camera uses `ACTION_IMAGE_CAPTURE`; our app needs no camera or storage
permission. Its FileProvider grants access to only one output URI and revokes it
when capture returns. Cancellation, blocked/missing camera apps, empty captures,
EXIF rotation/mirroring, and process recreation are handled.

Large photos are decoded with a maximum side of 2048 pixels. Both APIs and the
preview use that bitmap, so the result coordinates refer to the decoded image.
Captured files stay in private app cache, not the public gallery. Cancelled/empty
captures and replaced captures are removed. Cache may be cleared by Android.

Generated AAR/app model inputs survive `clean`; library test assets live under
`library/build/test-assets/` and must be prepared again afterwards. Fixture downloads
are pinned to a public MIT-licensed revision and verified by SHA-256. Use
`--photos /path/to/bp-tracker/test/fixtures` for local copies plus the upstream license.
Do not disable TLS certificate verification if a local Python installation has a
broken certificate setup; repair its CA configuration or use a configured Python.

## Test scope

| Suite | Coverage |
| --- | --- |
| JVM tests | Result contracts, immutable snapshots, NMS, mixed-text merging, crop geometry, cancellation and cleanup |
| 4 Python tests | Model/download integrity and rejection of corrupt assets |
| Library device tests | Numeric baseline, hybrid clock/label regressions, printed meter/sensor text, rotations, cancellation, invalid models, bitmap formats |
| App device tests | Hybrid AAR gallery output, gallery/camera results, lifecycle, permissions, cancellation, errors, EXIF, bounded decoding |

Camera intent-interception tests require API 26+ and are explicitly skipped on API 23.
The other tests run on both CI devices. Actual camera capture and recovery after
killing the demo process were also checked manually on an API 34 ARM64 emulator.
These are implementation checks, not broad model-accuracy evidence.

## CI and releases

CI runs on pushes to `main`, pull requests, and manual dispatch. It checks unit
tests, lint, a consumer of the actual AAR, and publication contents. A separate job
fetches/converts the pinned AGPL research model and caches it with the complete
upstream archive and notices. Android 23 and 34 jobs exercise real inference.
Only reports and model-free library artifacts are uploaded for distribution.

Actions are pinned by commit SHA; pull-request jobs have read-only repository
permissions and do not receive publishing secrets. Dependabot checks Gradle and
Actions monthly. Model-conversion pins change deliberately with parity validation.

To prepare a release:

1. Update `libraryVersion` in `gradle.properties`, integration examples, changelog,
   and `docs/RELEASE_NOTES.md`.
2. Merge to `main` and wait for CI to pass.
3. Push a matching annotated tag, such as `v0.1.0-alpha.1`.

The Release workflow reruns CI, checks that the tag matches the version, verifies
artifact hashes, then publishes a GitHub release. Versions containing `-` are
marked prereleases. It publishes an AAR, source JAR, Maven bundle, license/notices,
and SHA-256 checksums. No model-bearing APK is published automatically.
JitPack separately builds tagged source using `jitpack.yml`, Java 21, and the
required Android SDK packages. It runs only `:library:publishToMavenLocal` with
JitPack's `GROUP`, `ARTIFACT`, and `VERSION` coordinates; it does not fetch a model
or require the demo's local AAR. CI checks the same publication shape by compiling
an independent Android consumer with only the published dependency.

After tagging, request the version on JitPack and verify the actual hosted artifact:

```sh
python3 scripts/verify_maven_consumer.py --repository https://jitpack.io \
  --coordinate com.github.artemidius:7-segment-digits-Scanner:v0.2.0-alpha.1
```

JitPack and GitHub Actions are separate services. A green GitHub run alone does not
prove that JitPack built successfully; inspect its build log and the consumer check.
Maven Central publishing and Play Store deployment are not configured.

Local equivalent:

```sh
./gradlew :library:publishReleasePublicationToLocalDistributionRepository
python3 scripts/package_release.py
```
