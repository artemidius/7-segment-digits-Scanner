# Public recognition API

Package: `dev.sevendigits`. The hybrid API is unchanged from `0.2.0-alpha.1`, available from
JitPack. The earlier `0.1.0-alpha.1` uses the original numeric pipeline.

## Creation and ownership

```kotlin
val recognizer = SevenDigitsRecognizer.create(
    context,
    modelAssetPath = "seven-digits/detector.tflite",
    metadataAssetPath = "seven-digits/detector.json",
)
```

Creation loads and validates an immutable detector snapshot off the main thread.
The recognizer retains no Activity or Context. Supply a compatible detector and
metadata in app assets; see [the model contract](MODELS.md). ML Kit Latin text OCR
is a bundled Gradle dependency, so it needs no first-use model download.

The caller owns each input bitmap. Do not modify or recycle it before the call
finishes. After cancelling a job, join it before releasing its bitmap. Independent
calls can run concurrently. There is no public `close()` or manual resource lifecycle.

## Two views of the same result

```kotlin
typealias Lines = List<List<String>>

val lines: Lines = recognizer.recognizeText(bitmap)

val visual: RecognitionResult = recognizer.recognizeVisual(bitmap)
val sameLines: Lines = visual.toLines()
```

`Lines` is exactly a list of rows, each containing a list of words or numeric fields.
Rows run top to bottom and fields left to right in the upright image. Both list
levels are immutable. `recognizeText` calls the visual pipeline and projects its
result; use `visual.toLines()` to get both views without running recognition twice.

Example format (not an accuracy promise):

```kotlin
listOf(
    listOf("20", "00"),
    listOf("TEMP", "-12.5", "°C"),
    listOf("METER", "001234", "kWh"),
)
```

The library preserves letters, leading zeros, recognized signs, and punctuation.
It does not assign medical/date/time semantics, convert strings to numbers, add
missing separators, or restrict detection to a device-specific region. Text on
the housing or elsewhere in the image can also appear. The client interprets it.
Handwriting recognition and non-Latin OCR models are not included.

## Options

```kotlin
RecognitionOptions(
    rotationDegrees = 0,
    minimumCharacterConfidence = 0.25f,
    recognizePrintedText = true,
    detectSmallCharacters = true,
)
```

- `rotationDegrees`: clockwise correction, one of 0, 90, 180, or 270. Handle EXIF
  mirroring during decoding; use 0 if the bitmap is already upright.
- `minimumCharacterConfidence`: inclusive LiteRT character threshold in 0..1.
  General OCR preserves whole words and their low-scoring symbols; deleting a
  symbol from an otherwise recognized word can silently change its meaning.
- `recognizePrintedText`: run bundled ML Kit Latin OCR and merge with the detector.
  Setting it to false skips OCR execution but does not remove the SDK dependency.
- `detectSmallCharacters`: use nine overlapping half-size detector crops when the
  full frame would be downsampled by more than 2×, and a max-channel grayscale OCR
  view that reduces colored backlight interference. This increases latency and does not guarantee recall.

Set both Boolean options to false for the original single-pass numeric pipeline.
`supportedCharacters` remains the vocabulary of the app-supplied LiteRT model. It
is **not** a whitelist for hybrid output: general OCR can add letters and punctuation.

## Visual output

`RecognitionResult` contains the original `imageWidth`, `imageHeight`, immutable
`lines: List<List<TextRegion>>`, and flattened `regions` in upright reading order.

Each `TextRegion` contains an immutable character list and derives:

- `text`: the exact sequence of character values.
- `boundingBox`: the union of character boxes in original bitmap pixels.
- `minimumCharacterConfidence`: the smallest character score, useful for inspecting
  weak characters but not a calibrated probability of field accuracy.

Each `RecognizedCharacter` has `value: Char`, `boundingBox`, `confidence`, and
`source: RecognitionSource` (`SEVEN_SEGMENT` or `GENERAL_TEXT`). Visible letters,
numbers, punctuation, and units are accepted; whitespace/control characters are
not. A multi-unit UTF-16 OCR symbol shares its bounding box across its code units.
Scores are in 0..1 and are not comparable across the two engines.

Boxes have finite coordinates, positive area, and exclusive right/bottom edges.
They always fit within the original input bitmap, even after processing rotation
or cropping. Transform them into view coordinates when drawing overlays. Sorting
by original coordinates can destroy upright reading order on a rotated image.

## Pipeline and merging

The hybrid pipeline first makes the image upright, runs OCR on the original color
view and optionally a max-channel grayscale view, then runs the detector. All
recognition is on-device; see [ML Kit's terms](https://developers.google.com/ml-kit/terms)
for its separate SDK metrics behavior.

The detector uses RGB normalization and gray letterboxing, ignores enclosing-region
classes before class-agnostic NMS, and preserves full-frame detections while adding
non-overlapping zoom detections. Truncated detections at internal crop edges are
excluded. There are no blood-pressure-specific crops, label rules, or field limits.

Merging uses spatial overlap and reading order. General OCR supplies ordinary
words/units; detector characters take precedence in overlapping numeric fields.
Complete detector fields suppress duplicate OCR fragments, while recognized
punctuation is retained. Extra OCR views add uncovered text, including missing suffixes, while preserving
existing characters. These are heuristics, not a guarantee of correct conflict resolution.
No raw confidence comparison between engines decides which text wins.

## Cancellation and failures

The calling coroutine owns the work. Loading uses `Dispatchers.IO`; recognition
runs on `Dispatchers.Default`. Each call owns its interpreter, OCR client, and any
temporary bitmaps, and releases them in `finally` blocks.

LiteRT native invocations and ML Kit tasks may finish before cancellation returns.
The library waits for an in-flight OCR task before closing its client or releasing
its bitmap. It then propagates cancellation; it never leaves detached recognition
running against caller-owned image data. A cancelled recognizer remains reusable.

- Invalid input/options/result values: `IllegalArgumentException`.
- Model loading or recognition failures: `RecognitionException` (with a cause).
- Cancellation: `CancellationException`, never converted into an empty result.
- A successful empty outer list: no detected text. It is not proof the image is empty.

Partial output, missed symbols, and false positives remain possible. The current
research detector was trained on blood-pressure displays; general OCR and extra
views improve coverage but do not replace testing on each intended device family.

## Verification

JVM tests cover contracts, immutable snapshots, suppression, crop/rotation geometry,
merging labels with digits, punctuation, duplicates, and inference cleanup.
Device tests retain exact original single-pass numeric assertions, add clock digits
and labels on both pinned photos, and exercise printed thermometer/meter/sensor
text, rotated boxes, blank input, task cancellation, and hardware bitmaps.

The two real photos are development fixtures from the MIT-licensed
[bp-tracker](https://github.com/chakib-belgaid/bp-tracker/tree/ba4701e3e72767d096b48fb1b3277664a58b0138)
repository. They are not a held-out benchmark. Printed instrument test images are
generated in the tests; broad real thermometer/meter/sensor accuracy is unmeasured.

Recognition varies by Android/OCR runtime. Exact clock recovery is checked on
Android 14 (API 34). Earlier 0.2 tests on Android 6 (API 23) missed those clock
digits; the cause was not established. Current source supports API 24+ and tests
numeric baseline readings and printed instrument text on API 24 and 34.
Do not treat successful OCR as proof that every character was read.
