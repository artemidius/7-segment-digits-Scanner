# Models and provenance

The library combines an app-supplied LiteRT detector with bundled ML Kit Latin OCR.
No detector weights are included in our AAR or release bundle. The ML Kit Gradle
dependency packages Google’s OCR model into the consuming app. Supply a detector
whose license and accuracy fit your application.

## Tensor contract

| Item | Required format |
| --- | --- |
| Input | One float32 tensor `[1, height, width, 3]`, RGB / 255 |
| Resizing | Aspect-preserving letterbox, padding RGB `(114,114,114)` |
| Output | One float32 tensor `[1, 4 + classes, candidates]` |
| Boxes | `centerX, centerY, width, height` in input pixels |
| Scores | Per-class values in `[0,1]`; no separate objectness channel |
| Dimensions | Fixed positive height, width, and candidate count |

`detector.json` declares `box_units: "input_pixels"`, a `names` object with contiguous
class IDs starting at 0, and optionally `model_sha256`. The setup scripts require
the checksum; the runtime verifies it when present. Preserve the model's actual
class order. Unknown labels such as enclosing display regions are ignored before NMS.

Supported **LiteRT model labels** are `0123456789.,:-/+`. The aliases `dot`, `comma`, `colon`,
`minus`, and `plus` are recognized. The loaded model determines which symbols can
actually be detected. The library does not parse, identify, or filter dates/times.

## Optional SMART-BP research baseline

This is an opt-in **AGPL-3.0** model from Nikookar et al.,
[BP Image Digitize v1.2.0](https://zenodo.org/records/21480466), commit
`c71450adf67247e41674394e8f26ae873e040345`. The upstream README also describes
Ultralytics and commercial licensing considerations. Read the upstream terms
before packaging a model-bearing application. Apache-2.0 licensing of our engine
does not relicense those weights.

The baseline has digit classes `0–9` plus an enclosing-reading class named `10`.
It has no punctuation classes and misses small digits in the example monitor's
top row. Tests on two development photos read the main fields `126`, `81`, `81`;
this is not an independent accuracy benchmark or a guarantee of complete-screen OCR.
The hybrid pipeline additionally recovers the top-row digits (`2001` / `20`, `00`)
and printed labels in those fixtures through general OCR, including a max-channel
grayscale view for colored backlighting. It does not infer a colon or interpret a
time. This does not fix the detector’s underlying training bias: cropped clock
digits can still be misclassified. Other device families need representative tests.
Handwriting and non-Latin script OCR are outside this implementation.

Recognition varies by Android/OCR runtime. Exact clock recovery is checked on
Android 14 (API 34). Earlier 0.2 tests on Android 6 (API 23) missed those clock
digits; the cause was not established. Current source supports API 24+ and tests
numeric baseline readings and printed instrument text on API 24 and 34.
Do not treat successful OCR as proof that every character was read.

### Fetch and convert

Python 3.12 and several GB of free disk space are needed for the optional conversion
environment. It runs on CPU; an M1 Mac is sufficient. Conversion has been exercised
locally on Apple Silicon; CI also checks the Linux export through Android inference.

```sh
python3.12 -m venv .venv-model
.venv-model/bin/python -m pip install -r tools/model/requirements.txt
.venv-model/bin/python scripts/fetch_research_model.py --accept-agpl
.venv-model/bin/python tools/model/convert_detector.py \
  models/smart-bp/SMART-BP_Weights.pt --output models/smart-bp
```

On Linux, install the matching CPU PyTorch wheels first to avoid CUDA downloads:

```sh
.venv-model/bin/python -m pip install torch==2.15.0 torchvision==0.29.2 \
  --index-url https://download.pytorch.org/whl/cpu
```

The fetcher verifies the complete upstream archive and the extracted weights before
use. It retains the upstream license and README. Conversion exports YOLO to ONNX,
folds constants before layout conversion, and produces float16 weights with float32
input/output. The result is about 22.4 MB with input `[1,640,640,3]` and output
`[1,15,8400]`. Its JSON records the source and output hashes.

| Source | SHA-256 |
| --- | --- |
| Upstream v1.2.0 archive | `e36ef620c04213ca4245c166fcb75fce76078a0cc6907c7e885038c55ac5fd5a` |
| SMART-BP weights | `7b01cce5eaa6d08f9150fa4769bf11368f7bb6717b3983c1e904b801ac21bc7d` |
| Original Apple Silicon LiteRT export | `a795188f8dce78f2a1409e0dfca9a845ac1f4afedb9f192ad222550f650420d6` |

Conversion tools may produce different byte hashes on different platforms. Always
use the JSON generated with that export and run the device tests. All downloaded
weights, source archives, exports, and model-bearing app assets are Git-ignored.
Use `--archive /path/to/upstream.zip` to fetch from a verified local archive.

### Use an existing compatible export

No Python ML environment is needed if you already have a compatible export:

```sh
python3 scripts/prepare_test_app.py \
  --model-dir /path/to/export --model-license /path/to/MODEL_LICENSE
python3 scripts/prepare_instrumentation_assets.py \
  --model-dir /path/to/export --model-license /path/to/MODEL_LICENSE
```

The real-photo device assertions currently target SMART-BP. A replacement model
needs its own expected results and separate held-out evaluation. Do not relax the
existing assertions simply to make a different model pass.
