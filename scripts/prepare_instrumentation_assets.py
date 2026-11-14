"""Prepare ignored Android test assets from the model experiment and two pinned MIT photos."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
REVISION = 'ba4701e3e72767d096b48fb1b3277664a58b0138'
UPSTREAM = f'https://raw.githubusercontent.com/chakib-belgaid/bp-tracker/{REVISION}/'
PHOTOS = {
    '126-81-81-a.jpg': 'd77e19eee9f84abf2031145ffe73ea0b23fb03691e3622843d2e38cd03dec1b8',
    '126-81-81-b.jpg': '22d5e40e65e38f81dcb9ba6a743eb0e2d0349a737dc9b79d15bf552c540cce18',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model-dir', type=Path,
                        default=ROOT.parent/'7-Digits/experiments/local/smart-bp-litert')
    parser.add_argument('--model-license', type=Path,
                        default=ROOT.parent/'7-Digits/experiments/local/smart-bp/LICENSE')
    parser.add_argument('--photos', type=Path, help='Use local pinned photos instead of downloading them.')
    args = parser.parse_args()
    model = (args.model_dir/'detector.tflite').read_bytes()
    metadata = json.loads((args.model_dir/'detector.json').read_text())
    if hashlib.sha256(model).hexdigest() != metadata['model_sha256']:
        raise ValueError('Model checksum does not match metadata.')
    license_text = args.model_license.read_text()
    target = ROOT/'library/build/test-assets'
    (target/'seven-digits').mkdir(parents=True, exist_ok=True)
    (target/'fixtures').mkdir(parents=True, exist_ok=True)
    (target/'seven-digits/detector.tflite').write_bytes(model)
    (target/'seven-digits/detector.json').write_text(json.dumps(metadata))
    (target/'seven-digits/MODEL_LICENSE').write_text(license_text)
    for name, expected in PHOTOS.items():
        data = (args.photos/name).read_bytes() if args.photos else urllib.request.urlopen(
            UPSTREAM+'test/fixtures/'+name, timeout=30).read()
        if hashlib.sha256(data).hexdigest() != expected:
            raise ValueError(f'Photo checksum mismatch: {name}')
        (target/'fixtures'/name).write_bytes(data)
    license_data = (args.photos.parent.parent/'LICENSE').read_bytes() if args.photos else \
        urllib.request.urlopen(UPSTREAM+'LICENSE', timeout=30).read()
    (target/'fixtures/LICENSE').write_bytes(license_data)
    for name, update in [('wrong-hash', {'model_sha256': '0'*64}),
                         ('wrong-units', {'box_units': 'normalized'}),
                         ('wrong-label-count', {'names': {'0': '0'}})]:
        (target/'fixtures'/f'{name}.json').write_text(json.dumps({**metadata, **update}))
    unverified = {k: v for k, v in metadata.items() if k != 'model_sha256'}
    (target/'fixtures/unverified.json').write_text(json.dumps(unverified))
    (target/'fixtures/corrupt.tflite').write_bytes(b'not a model')
    print(f'Prepared assets in {target}. They are test-only and excluded from the published AAR.')


if __name__ == '__main__':
    main()
