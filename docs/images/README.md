# Demo screenshot

[gallery.png](gallery.png) shows real recognition by the demo consuming
`com.github.artemidius:7-segment-digits-Scanner:v0.2.0-alpha.1` from JitPack.
Captured on 2026-11-15 using an Android 14 / API 34 ARM64 Pixel 3a emulator
(1080 × 2220 display, density 440).

This is a scrolling capture of the app's actual laid-out content, ending after
`Lines`. System bars and the longer visual JSON section are outside the capture.
The photo, boxes, and recognition output are unedited. Floating text labels are
omitted by the demo UI to keep the photo readable; recognized text appears below.
[gallery-lines.txt](gallery-lines.txt) contains the exact displayed string output.

The clock appears as `2001`; the main readings, printed labels, units, and button
text are also detected. Some symbols are missed or mistaken for letters. This
single example demonstrates whole-photo input and raw output, not complete or
perfect recognition across devices.

## Inputs and attribution

- Photo: [`test/fixtures/126-81-81-a.jpg`](https://github.com/chakib-belgaid/bp-tracker/blob/ba4701e3e72767d096b48fb1b3277664a58b0138/test/fixtures/126-81-81-a.jpg)
  from Chakib Belgaid's bp-tracker, pinned revision
  `ba4701e3e72767d096b48fb1b3277664a58b0138`.
- Photo SHA-256: `d77e19eee9f84abf2031145ffe73ea0b23fb03691e3622843d2e38cd03dec1b8`.
- Photo license: [MIT](../../third_party/bp-tracker/LICENSE).
- Optional SMART-BP detector: [provenance and AGPL terms](../MODELS.md).
- Converted detector SHA-256:
  `a795188f8dce78f2a1409e0dfca9a845ac1f4afedb9f192ad222550f650420d6`.
  Conversion on another platform can produce different bytes; preserve metadata
  matching the model you use.

## Reproduce

Prepare the model as described in [model setup](../MODELS.md). From the repository
root, with an API 34+ emulator attached (substitute its serial):

```sh
python3 scripts/prepare_test_app.py
python3 scripts/prepare_instrumentation_assets.py
./gradlew :test-app:assembleDebug :test-app:assembleDebugAndroidTest
adb -s emulator-5554 install -r test-app/build/outputs/apk/debug/test-app-debug.apk
adb -s emulator-5554 install -r test-app/build/outputs/apk/androidTest/debug/test-app-debug-androidTest.apk
adb -s emulator-5554 shell run-as dev.sevendigits.testapp mkdir -p cache
adb -s emulator-5554 shell run-as dev.sevendigits.testapp sh -c '"cat > cache/readme-fixture.jpg"' < library/build/test-assets/fixtures/126-81-81-a.jpg
adb -s emulator-5554 shell am instrument -w -r \
  -e class dev.sevendigits.testapp.ReadmeScreenshotCapture \
  -e captureReadme true \
  dev.sevendigits.testapp.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 exec-out run-as dev.sevendigits.testapp cat cache/readme-gallery.png > docs/images/gallery.png
adb -s emulator-5554 exec-out run-as dev.sevendigits.testapp cat cache/readme-lines.txt > docs/images/gallery-lines.txt
adb -s emulator-5554 exec-out run-as dev.sevendigits.testapp cat cache/readme-visual.json > /tmp/seven-digits-readme-visual.json
```

The opt-in helper feeds the fixture through the gallery result handler, waits for
both real API calls, checks key results, and captures the app's view with Canvas.
It is skipped in regular CI runs. The full visual JSON is saved in app cache for
inspection. Output can vary by Android/OCR runtime; inspect a new capture before
replacing the committed image.
