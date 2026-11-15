"""Fetch pinned SMART-BP source weights and notices. Conversion is a separate step."""
import argparse
import io
from pathlib import Path
import zipfile
from assets import DEFAULT_MODEL, checked_bytes, download

URL = 'https://zenodo.org/records/21480466/files/cliffordlab/BP_image_digitize-v.1.2.0.zip'
SHA256 = 'e36ef620c04213ca4245c166fcb75fce76078a0cc6907c7e885038c55ac5fd5a'
WEIGHTS_SHA256 = '7b01cce5eaa6d08f9150fa4769bf11368f7bb6717b3983c1e904b801ac21bc7d'
PREFIX = 'cliffordlab-BP_image_digitize-c71450a/'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--accept-agpl', action='store_true', help='Acknowledge the separate upstream AGPL model terms.')
    parser.add_argument('--output', type=Path, default=DEFAULT_MODEL)
    parser.add_argument('--archive', type=Path, help='Use an already downloaded pinned upstream archive.')
    args = parser.parse_args()
    if not args.accept_agpl:
        parser.error('Read docs/MODELS.md and upstream terms, then opt in with --accept-agpl.')
    archive = args.archive or args.output / 'upstream.zip'
    data = download(URL, SHA256, archive)
    with zipfile.ZipFile(io.BytesIO(data)) as package:
        # Extract only known members; never extract arbitrary archive paths.
        weights = checked_bytes(package.read(PREFIX + 'Saved_Models/SMART-BP_Weights.pt'), WEIGHTS_SHA256, 'weights')
        args.output.mkdir(parents=True, exist_ok=True)
        (args.output / 'SMART-BP_Weights.pt').write_bytes(weights)
        for source, target in [('LICENSE', 'MODEL_LICENSE'), ('README.md', 'UPSTREAM_README.md')]:
            (args.output / target).write_bytes(package.read(PREFIX + source))
    print(f'Fetched AGPL research weights into {args.output}. Convert with tools/model/convert_detector.py.')


if __name__ == '__main__':
    main()
