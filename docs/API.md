# Public recognition API

Package: `dev.sevendigits`. This is an initial 0.1 API contract, subject to refinement
before a stable release. All public declarations have KDoc and explicit visibility/types.

Both entry points are backed by a private LiteRT CPU implementation. The public
factory loads a model and metadata from app assets. The test app consumes the
library as a local AAR and demonstrates both outputs for a gallery image.

## Model assets

```kotlin
val recognizer = SevenDigitsRecognizer.create(
    context,
    modelAssetPath = "seven-digits/detector.tflite",
    metadataAssetPath = "seven-digits/detector.json",
)
```

Both paths are relative to app assets and have the defaults shown. Creation is
suspending and safe to call from the main thread. It reads an immutable snapshot,
verifies the optional checksum, validates the model's tensors with LiteRT, and
returns a recognizer without retaining the Context. No network access occurs.

Supported model contract:

- One float32 input `[1, height, width, 3]`, RGB normalized to 0..1.
- One float32 output `[1, 4 + classCount, candidateCount]` containing xywh boxes
  in input pixels followed by per-class scores in 0..1.
- Fixed positive image dimensions and candidate count. Floating-point weights,
  including float16 weights with float32 input/output, are supported.
- Metadata JSON containing `box_units: "input_pixels"` and a `names` object with
  contiguous class IDs starting at zero. Optional `model_sha256` pins the model bytes.

Example metadata for a model with three digit classes and one enclosing-region class:

```json
{
  "box_units": "input_pixels",
  "names": {"0": "0", "1": "1", "2": "2", "3": "display"}
}
```

Use the actual labels and class order of your model. The SMART-BP experiment has
11 classes and an unusual order; its class named `10` means a reading region.
Unknown/non-character labels are excluded before suppression. Names such as
`dot`, `comma`, `colon`, `minus`, and `plus` map to the corresponding characters.

The Android AAR includes the engine and Maven dependencies, not model weights.
The optional SMART-BP model can be fetched and converted using the pinned
[model setup](MODELS.md). Retain its upstream licensing terms when packaging that
model; selecting a general-purpose release model remains separate.

## Two entry points

```kotlin
typealias Lines = List<List<String>>

interface SevenDigitsRecognizer {
    val supportedCharacters: Set<Char>

    suspend fun recognizeText(
        bitmap: android.graphics.Bitmap,
        options: RecognitionOptions = RecognitionOptions(),
    ): Lines

    suspend fun recognizeVisual(
        bitmap: android.graphics.Bitmap,
        options: RecognitionOptions = RecognitionOptions(),
    ): RecognitionResult
}
```

`recognizeText` returns only rows of strings. Each string is a detected field,
such as `"120"` or `"11:05"`, not an individual character. The outer list orders
rows top to bottom; each inner list orders fields left to right. Both orders refer
to the upright image. This is exactly `List<List<String>>`, with no wrapper object
or visual metadata. Returned lists are immutable at both levels.

`recognizeVisual` returns the detailed `RecognitionResult`. Its `lines` property
holds the same grouping as `List<List<TextRegion>>`; `regions` flattens the fields
for drawing overlays. `visual.toLines()` projects those rows to strings without
running inference again, sorting by original-image coordinates, or joining fields.

The default `recognizeText` implementation calls `recognizeVisual` once and returns
`toLines()`. Both paths therefore share detection, options, grouping, and errors.
To obtain both representations, call `recognizeVisual` and then `toLines()`;
calling both entry points separately performs two recognition operations.

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

## Visual output

`RecognitionResult.lines` is an immutable list of immutable rows of `TextRegion`
instances. Rows must be nonempty; no detections is an empty outer list. A region
contains an immutable list of `RecognizedCharacter` values; it derives:

- `text`: the exact character sequence, preserving signs, punctuation, and leading zeros.
- `boundingBox`: the union of its character boxes.
- `minimumCharacterConfidence`: the lowest score among its characters.

A character contains `value`, `boundingBox`, and `confidence`. Supported numeric
symbols in the API are `0–9`, `.`, `,`, `:`, `-`, `/`, and `+`. Each model supports
its own subset. Scores are model-dependent and are not calibrated probabilities
that a character or an entire field is correct.

For an image with three rows containing these fields, the text API would return:

```kotlin
listOf(
    listOf("120"),
    listOf("80", "65"),
    listOf("11.11.2026", "11:05"),
)
```

This illustrates the data format, not current model performance. The research
SMART-BP baseline still misses clock digits and lacks punctuation classes.

The API returns detected characters across the display without identifying dates,
times, or medical measurements. It does not filter out date/time content, assign
SYS/DIA/pulse labels, convert strings to numbers or dates, insert missing characters,
or claim a reading is complete. Meaning is exclusively the client's responsibility. A successful empty
outer list means nothing met the detection criteria. Partial results are also possible.
Empty rows and placeholder strings are not inserted to represent gaps or missed content.

## Execution and failures

The calling coroutine owns each recognition operation. Calls are independent and
may run concurrently on the same recognizer. The runtime implementation must move
expensive work off the calling thread, including when called from the main thread.
It must use structured concurrency, keep inference resources local to the call,
and release them in `finally` on completion, failure, or cancellation. It must
finish its child work before returning and must not use detached background jobs.
The supplied implementation uses `Dispatchers.IO` for loading and `Dispatchers.Default`
for recognition, with cancellation checks between stages and during preprocessing,
decoding, and grouping. Each call constructs its own CPU interpreter from shared
read-only model data and releases it with `finally`/`use` before returning. Native
inference is not interrupted mid-call; cancellation is checked immediately afterwards.
This trades interpreter initialization per photo for isolated, automatically managed calls.

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

Unit tests cover multiple fields per row, separate rows, exact string preservation,
reading order after rotation, small punctuation bounds, minimum scores, defensive
collection snapshots and text projection at both list levels, empty detections,
image boundaries, and invalid/non-finite values. Decoder tests cover ignored classes,
overlapping detections, clipping, threshold boundaries, row/field grouping, punctuation,
all rotation transforms, and odd aspect ratios. Controlled blocking-session tests
verify cleanup on success/failure and that cancellation waits for a native call to
finish before releasing resources.

Six device tests ran on Android 14 / API 34 ARM64 using the real converted SMART-BP
model. Both full-photo fixtures returned `[["126"], ["81"], ["81"]]` through text and
visual APIs. Tests also cover rotated photos and box coordinates, concurrent calls,
cancellation and reuse, invalid model assets, blank/recycled images, normalization,
padding, and hardware bitmaps. The 19 JVM tests and all device tests passed.

The two photos are development fixtures from the MIT-licensed
[bp-tracker](https://github.com/chakib-belgaid/bp-tracker/tree/ba4701e3e72767d096b48fb1b3277664a58b0138)
repository. This verifies the Android pipeline on those examples, not recognition
accuracy across devices. Clock/date recognition remains limited by the selected model.

## Private pipeline

The implementation uses the maintained LiteRT 1.4.2 Interpreter branch for CPU
inference ([Android runtime versions](https://developers.google.com/edge/litert/android)).
It applies rotation and aspect-preserving resizing into a gray (114) letterbox,
normalizes RGB pixels, then decodes model candidates. Character filtering precedes
class-agnostic IoU suppression at 0.45 so enclosing reading boxes do not hide digits.
Grouping uses digit-row geometry, attaches nearby punctuation, splits wide horizontal
gaps into fields, and maps boxes back to the original bitmap after grouping.

These operations are implementation details; neither LiteRT types nor native resource
management appears in the public recognition API. The model is loaded once per factory
call; the process/GC manages the immutable model buffer's lifetime.
