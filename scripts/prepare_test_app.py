"""Copy the built AAR and research model into the standalone AAR-consuming test app."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--aar', type=Path, default=ROOT/'library/build/outputs/aar/library-release.aar')
    parser.add_argument('--model-dir', type=Path,
                        default=ROOT.parent/'7-Digits/experiments/local/smart-bp-litert')
    parser.add_argument('--model-license', type=Path,
                        default=ROOT.parent/'7-Digits/experiments/local/smart-bp/LICENSE')
    args = parser.parse_args()
    for path in [args.aar, args.model_dir/'detector.tflite', args.model_dir/'detector.json', args.model_license]:
        if not path.is_file():
            parser.error(f'Missing {path}. Build :library:assembleRelease first and provide the model inputs.')
    model = (args.model_dir/'detector.tflite').read_bytes()
    metadata = json.loads((args.model_dir/'detector.json').read_text())
    if hashlib.sha256(model).hexdigest() != metadata['model_sha256']:
        parser.error('Model checksum does not match metadata.')
    libs = ROOT/'test-app/libs'
    assets = ROOT/'test-app/src/main/assets/seven-digits'
    libs.mkdir(parents=True, exist_ok=True)
    assets.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(args.aar, libs/'seven-digits.aar')
    (assets/'detector.tflite').write_bytes(model)
    shutil.copyfile(args.model_dir/'detector.json', assets/'detector.json')
    shutil.copyfile(args.model_license, assets/'MODEL_LICENSE')
    print('Prepared test-app/libs/seven-digits.aar and app model assets (all Git-ignored).')


if __name__ == '__main__':
    main()
