# Changelog

## Unreleased

- Update pinned GitHub Actions, Gradle to 9.9.1, and demo AndroidX Core to 1.19.1.
- Verify CI distribution upload/download with the same action used by releases.

- Make the demo consume the pinned JitPack release, retaining manual AAR integration instructions.
- Refresh the README with whole-photo hybrid OCR output and a reproducible screenshot capture.
- Keep dense display previews readable by drawing boxes without floating text labels.

## 0.2.0-alpha.1

- Add JitPack delivery with transitive runtime dependencies and an Android consumer check.
- Combine the LiteRT detector with bundled ML Kit Latin OCR; return printed labels, units, and numeric fields.
- Add overlapping detector views and max-channel grayscale OCR for small/backlit text.
- Recover previously missed clock digits in both pinned development photos.
- Merge spatial results, preserve OCR words and punctuation, and report each character’s source.
- Keep `Lines` unchanged; allow visible text characters in the visual API.
- Add opt-outs for printed text and extra small-character passes.
- Add hybrid/rotation/cancellation regression tests and document the new AAR dependency.

No handwriting model is included. Broader device accuracy is not yet benchmarked.

## 0.1.0-alpha.1

Initial public preview:

- Coroutine-based whole-image recognition with text and visual APIs.
- Rotation, letterboxing, character suppression, row/field grouping, and input-coordinate boxes.
- Automatic native cleanup, cancellation propagation, and concurrent recognition calls.
- Gallery/camera demo importing the library as a local AAR.
- Model-free AAR/Maven bundles, pinned research-model conversion, CI, and integration guides.

The API is not stable yet. Models are supplied separately; the optional SMART-BP
baseline has limited small-digit coverage and no punctuation classes.
