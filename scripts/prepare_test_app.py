"""Prepare research model assets for the JitPack-consuming demo; optionally copy an AAR for file integration."""
import argparse
from pathlib import Path
import shutil
from assets import DEFAULT_MODEL, ROOT, read_model



def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--aar', type=Path, help='Optionally copy this AAR to test-app/libs; does not change the Gradle dependency.')
    parser.add_argument('--model-dir', type=Path, default=DEFAULT_MODEL)
    parser.add_argument('--model-license', type=Path, help='Defaults to MODEL_LICENSE inside --model-dir.')
    parser.add_argument('--aar-only', action='store_true', help='Copy only an AAR for manual file integration; defaults to the local release build.')
    args = parser.parse_args()
    args.model_license = args.model_license or args.model_dir/'MODEL_LICENSE'
    if args.aar_only and args.aar is None:
        args.aar = ROOT/'library/build/outputs/aar/library-release.aar'
    if args.aar is not None:
        if not args.aar.is_file():
            parser.error('Build :library:assembleRelease first or provide --aar.')
        libs = ROOT/'test-app/libs'
        libs.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(args.aar, libs/'seven-digits.aar')
    if args.aar_only:
        print('Copied AAR for manual file integration. The demo still uses its declared JitPack dependency.')
        return
    for path in [args.model_dir/'detector.tflite', args.model_dir/'detector.json', args.model_license]:
        if not path.is_file():
            parser.error(f'Missing {path}. Provide the model inputs; no local library build is needed.')
    model, _ = read_model(args.model_dir)
    assets = ROOT/'test-app/src/main/assets/seven-digits'
    assets.mkdir(parents=True, exist_ok=True)
    (assets/'detector.tflite').write_bytes(model)
    shutil.copyfile(args.model_dir/'detector.json', assets/'detector.json')
    shutil.copyfile(args.model_license, assets/'MODEL_LICENSE')
    print('Prepared demo model assets (Git-ignored). The scanner library resolves through Gradle/JitPack.')


if __name__ == '__main__':
    main()
