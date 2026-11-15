"""Copy the built AAR and research model into the standalone AAR-consuming test app."""
import argparse
from pathlib import Path
import shutil
from assets import DEFAULT_MODEL, ROOT, read_model



def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--aar', type=Path, default=ROOT/'library/build/outputs/aar/library-release.aar')
    parser.add_argument('--model-dir', type=Path, default=DEFAULT_MODEL)
    parser.add_argument('--model-license', type=Path, help='Defaults to MODEL_LICENSE inside --model-dir.')
    parser.add_argument('--aar-only', action='store_true', help='Prepare a model-free app build; recognition needs model assets.')
    args = parser.parse_args()
    args.model_license = args.model_license or args.model_dir/'MODEL_LICENSE'
    if not args.aar.is_file():
        parser.error('Build :library:assembleRelease first or provide --aar.')
    libs = ROOT/'test-app/libs'
    libs.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(args.aar, libs/'seven-digits.aar')
    if args.aar_only:
        print('Copied AAR only. Existing model assets, if any, were left unchanged.')
        return
    for path in [args.aar, args.model_dir/'detector.tflite', args.model_dir/'detector.json', args.model_license]:
        if not path.is_file():
            parser.error(f'Missing {path}. Build :library:assembleRelease first and provide the model inputs.')
    model, _ = read_model(args.model_dir)
    assets = ROOT/'test-app/src/main/assets/seven-digits'
    assets.mkdir(parents=True, exist_ok=True)
    (assets/'detector.tflite').write_bytes(model)
    shutil.copyfile(args.model_dir/'detector.json', assets/'detector.json')
    shutil.copyfile(args.model_license, assets/'MODEL_LICENSE')
    print('Prepared test-app/libs/seven-digits.aar and app model assets (all Git-ignored).')


if __name__ == '__main__':
    main()
