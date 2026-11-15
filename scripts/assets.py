"""Shared integrity checks for local model and fixture preparation (standard library only)."""
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MODEL = ROOT / 'models/smart-bp'


def checked_bytes(data, expected, label):
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError(f'SHA-256 mismatch: {label}')
    return data


def download(url, expected, destination):
    """Verify before writing; never replace a valid cached download with partial data."""
    destination = Path(destination)
    if destination.exists():
        return checked_bytes(destination.read_bytes(), expected, destination)
    with urllib.request.urlopen(url, timeout=120) as response:
        data = checked_bytes(response.read(), expected, url)
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + '.tmp')
    temporary.write_bytes(data)
    temporary.replace(destination)
    return data


def read_model(directory):
    directory = Path(directory)
    metadata = json.loads((directory / 'detector.json').read_text())
    model = checked_bytes((directory / 'detector.tflite').read_bytes(), metadata['model_sha256'], directory)
    return model, metadata
