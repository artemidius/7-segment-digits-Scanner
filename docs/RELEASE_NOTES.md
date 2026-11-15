LiteRT 2.3.2 compatibility update (0.3.0-alpha.1).

**Breaking requirements:** Android 7+ (API 24) and Kotlin 2.4 are now required.
The build uses Kotlin 2.4.21 with AGP's built-in Kotlin support. The public APIs
and CPU Interpreter inference pipeline are unchanged. See the integration guide
for the compiler configuration when upgrading from 0.2.

Hybrid on-device OCR for seven-segment displays and printed Latin text.

This alpha combines LiteRT with bundled ML Kit, adds small-character/backlight
processing, and merges raw text with bounding boxes and per-character provenance.
Both pinned example photos now include their previously missed clock digits.
The text API remains `List<List<String>>`. No handwriting model is included.

Install through JitPack:

```kotlin
implementation("com.github.artemidius:7-segment-digits-Scanner:v0.3.0-alpha.1")
```

See [integration instructions](https://github.com/artemidius/7-segment-digits-Scanner/blob/main/docs/INTEGRATION.md)
for repository configuration, model assets, or direct AAR/Maven bundle use.
Runtime dependencies resolve transitively through JitPack. GitHub downloads have
SHA-256 checksums in `SHA256SUMS`.

**Detector model required:** no detector weights are included. Optional SMART-BP
weights retain their separate AGPL terms. ML Kit's SDK/model has Google's own terms.
Accuracy across real thermometers, meters, and sensors is not yet benchmarked;
partial results and false positives remain possible. This is an alpha API.

Recognition varies by Android/OCR runtime. Exact clock recovery is checked on
Android 14 (API 34). Earlier 0.2 tests on Android 6 (API 23) missed those clock
digits; the cause was not established. Current source supports API 24+ and tests
numeric baseline readings and printed instrument text on API 24 and 34.
Do not treat successful OCR as proof that every character was read.
