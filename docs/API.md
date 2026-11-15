# Public recognition API

Package: `dev.sevendigits`. This is an initial 0.1 API contract, subject to refinement
before a stable release. All public declarations have KDoc and explicit visibility/types.

**Implemented now:** the recognizer interface, options, exception, and immutable,
validated result types. **Next:** a concrete recognizer, creation/loading API,
and LiteRT integration. This artifact cannot yet perform image recognition.
The test app remains an empty activity.

## Entry point

```kotlin
interface SevenDigitsRecognizer {
    val supportedCharacters: Set<Char>

    suspend fun recognize(
        bitmap: android.graphics.Bitmap,
        options: RecognitionOptions = RecognitionOptions(),
    ): RecognitionResult
}
```

Input is the **whole photo** as a bitmap. Detection, scaling, character recognition,
and grouping belong inside the implementation. Callers do not crop individual digits.
The bitmap belongs to the caller; keep it unchanged and unrecycled until the call
finishes. The library must not modify or recycle it.

`supportedCharacters` describes the loaded model's vocabulary. For example, a
digits-only model cannot promise `11:05` or `11.11.2026` even though the result types
can represent those strings. Vocabulary support alone does not establish accuracy.

## Options and coordinates

`RecognitionOptions` has two fields:

- `rotationDegrees = 0`: clockwise correction to make the bitmap upright; allowed
  values are 0, 90, 180, and 270. Apply any EXIF mirroring while decoding. If the
  bitmap is already upright, use 0 regardless of the original file's EXIF value.
- `minimumCharacterConfidence = 0.25f`: inclusive per-character score threshold,
  finite and in 0..1. Raising it can omit digits and leave partial readings.

`RecognitionResult.imageWidth` and `imageHeight` always describe the supplied
bitmap. Every `BoundingBox` uses that original coordinate system, including when
rotation is requested. A runtime must undo its processing rotation and scaling
before returning boxes. This lets an app draw overlays on the original bitmap.

Boxes contain finite float coordinates, start at the top-left, and use exclusive
right/bottom edges. They have positive area and stay inside the image boundaries.
Regions and their characters follow **upright reading order**, which can differ
from coordinate order in a rotated original bitmap.

## Output

`RecognitionResult.regions` is an immutable list of `TextRegion` instances. A region
contains an immutable list of `RecognizedCharacter` values; it derives:

- `text`: the exact character sequence, preserving signs, punctuation, and leading zeros.
- `boundingBox`: the union of its character boxes.
- `minimumCharacterConfidence`: the lowest score among its characters.

A character contains `value`, `boundingBox`, and `confidence`. Supported numeric
symbols in the API are `0–9`, `.`, `,`, `:`, `-`, `/`, and `+`. Each model supports
its own subset. Scores are model-dependent and are not calibrated probabilities
that a character or an entire field is correct.

For the example display, possible region texts would be:

```text
120
80
65
11.11.2026
11:05
```

This illustrates the data format, not current model performance. The research
SMART-BP baseline still misses clock digits and lacks punctuation classes.

The API does not assign SYS/DIA/pulse labels, convert strings to numbers or dates,
insert missing characters, or claim a reading is complete. A successful empty
list means nothing met the detection criteria. Partial results are also possible.

## Execution and failures

The calling coroutine owns each recognition operation. Calls are independent and
may run concurrently on the same recognizer. The runtime implementation must move
expensive work off the calling thread, including when called from the main thread.
It must use structured concurrency, keep inference resources local to the call,
and release them in `finally` on completion, failure, or cancellation. It must
finish its child work before returning and must not use detached background jobs.
A `suspend` declaration alone does not provide this behavior; these are requirements
for the forthcoming implementation.

- Invalid options, result values, or unreadable/recycled input: `IllegalArgumentException`.
- Operational failures, including inference/model resource failures: `RecognitionException`.
- Coroutine cancellation: propagate cancellation unchanged; never return empty success
  or wrap it as `RecognitionException`. A native invocation may finish before the
  call can return. The recognizer remains usable afterwards.

Catch `RecognitionException` for recognition failures; diagnostic messages are not
a stable machine-readable code or user-facing copy. Empty detections are ordinary
successful results, distinct from failures.

For example, recognition can run inside `viewModelScope.launch { ... }`; cancellation
of that coroutine also cancels recognition. The native invocation may need to
finish before resources can safely be released. The bitmap remains caller-owned:
after cancelling a job, await its completion before modifying or recycling it.
The recognizer remains reusable; there is no manual shutdown step.

## Verification

Unit tests cover preservation of whole-display strings, reading order after rotation,
small punctuation bounds, minimum scores, defensive collection snapshots, empty
detections, image boundaries, and invalid/non-finite values. Runtime scheduling,
cancellation, resource cleanup, coordinate transformation, and OCR accuracy will be tested
when the implementation exists; the current tests do not claim to verify inference.
