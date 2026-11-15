"""Prepare ignored Android test assets from the model experiment and two pinned MIT photos."""
import argparse
import json
from pathlib import Path
import urllib.request
from assets import DEFAULT_MODEL, ROOT, checked_bytes, read_model

REVISION = 'ba4701e3e72767d096b48fb1b3277664a58b0138'
UPSTREAM = f'https://raw.githubusercontent.com/chakib-belgaid/bp-tracker/{REVISION}/'
PHOTOS = {
    '126-81-81-a.jpg': 'd77e19eee9f84abf2031145ffe73ea0b23fb03691e3622843d2e38cd03dec1b8',
    '126-81-81-b.jpg': '22d5e40e65e38f81dcb9ba6a743eb0e2d0349a737dc9b79d15bf552c540cce18',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model-dir', type=Path, default=DEFAULT_MODEL)
    parser.add_argument('--model-license', type=Path, help='Defaults to MODEL_LICENSE inside --model-dir.')
    parser.add_argument('--photos', type=Path, help='Use local pinned photos instead of downloading them.')
    args = parser.parse_args()
    args.model_license = args.model_license or args.model_dir/'MODEL_LICENSE'
    model, metadata = read_model(args.model_dir)
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
        checked_bytes(data, expected, name)
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
