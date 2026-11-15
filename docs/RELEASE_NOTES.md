Hybrid on-device OCR for seven-segment displays and printed Latin text.

This alpha combines LiteRT with bundled ML Kit, adds small-character/backlight
processing, and merges raw text with bounding boxes and per-character provenance.
Both pinned example photos now include their previously missed clock digits.
The text API remains `List<List<String>>`. No handwriting model is included.

Install through JitPack:

```kotlin
implementation("com.github.artemidius:7-segment-digits-Scanner:v0.2.0-alpha.1")
```

See [integration instructions](https://github.com/artemidius/7-segment-digits-Scanner/blob/main/docs/INTEGRATION.md)
for repository configuration, model assets, or direct AAR/Maven bundle use.
Runtime dependencies resolve transitively through JitPack. GitHub downloads have
SHA-256 checksums in `SHA256SUMS`.

**Detector model required:** no detector weights are included. Optional SMART-BP
weights retain their separate AGPL terms. ML Kit's SDK/model has Google's own terms.
Accuracy across real thermometers, meters, and sensors is not yet benchmarked;
partial results and false positives remain possible. This is an alpha API.
